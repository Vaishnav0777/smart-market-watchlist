package com.smartwatch.watchlist.service;

import com.smartwatch.change.ChangeDetector;
import com.smartwatch.change.ChangeHighlights;
import com.smartwatch.change.ChangeSubject;
import com.smartwatch.change.ChangeType;
import com.smartwatch.change.DetectedChange;
import com.smartwatch.change.MarketSnapshot;
import com.smartwatch.change.MembershipDelta;
import com.smartwatch.common.web.ApiException;
import com.smartwatch.marketdata.entity.Instrument;
import com.smartwatch.marketdata.entity.MarketObservation;
import com.smartwatch.marketdata.model.Quote;
import com.smartwatch.marketdata.repository.InstrumentRepository;
import com.smartwatch.marketdata.service.MarketObservationService;
import com.smartwatch.watchlist.dto.ChangeResponse;
import com.smartwatch.watchlist.dto.ChangeSummaryResponse;
import com.smartwatch.watchlist.dto.WatchlistCheckResponse;
import com.smartwatch.watchlist.dto.WatchlistChangesResponse;
import com.smartwatch.watchlist.entity.Watchlist;
import com.smartwatch.watchlist.entity.WatchlistCheck;
import com.smartwatch.watchlist.entity.WatchlistCheckItem;
import com.smartwatch.watchlist.entity.WatchlistItem;
import com.smartwatch.watchlist.repository.WatchlistCheckItemRepository;
import com.smartwatch.watchlist.repository.WatchlistCheckRepository;
import com.smartwatch.watchlist.repository.WatchlistItemRepository;
import com.smartwatch.watchlist.repository.WatchlistRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class WatchlistChangeService {

    static final String WATCHLIST_NOT_FOUND = "Watchlist not found";
    static final String CURSOR_NOT_FOUND = "Check cursor not found";
    static final String INVALID_SINCE = "since must be a check cursor or an ISO-8601 timestamp";

    private final WatchlistRepository watchlistRepository;
    private final WatchlistItemRepository itemRepository;
    private final WatchlistCheckRepository checkRepository;
    private final WatchlistCheckItemRepository checkItemRepository;
    private final InstrumentRepository instrumentRepository;
    private final MarketObservationService observationService;
    private final ChangeDetector changeDetector;
    private final Clock clock;

    public WatchlistChangeService(
            WatchlistRepository watchlistRepository,
            WatchlistItemRepository itemRepository,
            WatchlistCheckRepository checkRepository,
            WatchlistCheckItemRepository checkItemRepository,
            InstrumentRepository instrumentRepository,
            MarketObservationService observationService,
            ChangeDetector changeDetector,
            Clock clock) {
        this.watchlistRepository = watchlistRepository;
        this.itemRepository = itemRepository;
        this.checkRepository = checkRepository;
        this.checkItemRepository = checkItemRepository;
        this.instrumentRepository = instrumentRepository;
        this.observationService = observationService;
        this.changeDetector = changeDetector;
        this.clock = clock;
    }

    /**
     * Compares the watchlist with a cursor. Does not move the cursor and
     * does not store the quotes it reads.
     */
    @Transactional(readOnly = true)
    public WatchlistChangesResponse changes(UUID userId, UUID watchlistId, String since) {
        Watchlist watchlist = requireOwned(userId, watchlistId);
        Baseline baseline = resolveBaseline(watchlistId, since);
        List<WatchlistItem> items = itemRepository.findDetailedByWatchlistId(watchlistId);
        List<Instrument> currentInstruments = items.stream().map(WatchlistItem::getInstrument).toList();
        Map<UUID, Quote> quotes = observationService.latestQuotes(currentInstruments);
        Map<UUID, MarketObservation> references = references(baseline, currentInstruments);

        List<ChangeSubject> subjects = new ArrayList<>();
        Set<UUID> currentIds = new HashSet<>();
        for (WatchlistItem item : items) {
            Instrument instrument = item.getInstrument();
            currentIds.add(instrument.getId());
            MembershipDelta membership = membership(baseline, instrument.getId(), item.getCreatedAt());
            subjects.add(new ChangeSubject(
                    instrument.getId(),
                    instrument.getSymbol(),
                    instrument.getExchange(),
                    membership,
                    snapshot(quotes.get(instrument.getId())),
                    snapshot(references.get(instrument.getId()))));
        }
        if (baseline != null && baseline.membership() != null) {
            Set<UUID> removedIds = new HashSet<>(baseline.membership().keySet());
            removedIds.removeAll(currentIds);
            if (!removedIds.isEmpty()) {
                for (Instrument instrument : instrumentRepository.findAllById(removedIds)) {
                    subjects.add(new ChangeSubject(
                            instrument.getId(),
                            instrument.getSymbol(),
                            instrument.getExchange(),
                            MembershipDelta.REMOVED,
                            null,
                            null));
                }
            }
        }

        List<DetectedChange> detected = changeDetector.detect(subjects, clock.instant());
        return new WatchlistChangesResponse(
                watchlist.getId(),
                baseline == null ? null : baseline.cursor(),
                baseline == null ? null : baseline.checkedAt(),
                detected.stream().map(ChangeResponse::from).toList(),
                summary(detected, baseline != null));
    }

    /**
     * Records the current quotes and membership, then returns a new cursor.
     */
    @Transactional
    public WatchlistCheckResponse acknowledge(UUID userId, UUID watchlistId) {
        Watchlist watchlist = requireOwned(userId, watchlistId);
        List<WatchlistItem> items = itemRepository.findDetailedByWatchlistId(watchlistId);
        List<Instrument> instruments = items.stream().map(WatchlistItem::getInstrument).toList();
        Map<UUID, MarketObservation> recorded = observationService.recordLatest(instruments);
        WatchlistCheck check = checkRepository.saveAndFlush(
                new WatchlistCheck(watchlist, watchlist.getUser(), clock.instant()));
        for (WatchlistItem item : items) {
            checkItemRepository.save(new WatchlistCheckItem(
                    check,
                    item.getInstrument(),
                    recorded.get(item.getInstrument().getId())));
        }
        return new WatchlistCheckResponse(watchlist.getId(), check.getId(), check.getCheckedAt());
    }

    private Map<UUID, MarketObservation> references(Baseline baseline, List<Instrument> currentInstruments) {
        if (baseline == null) {
            return Map.of();
        }
        if (baseline.membership() != null) {
            Map<UUID, MarketObservation> references = new HashMap<>();
            for (Instrument instrument : currentInstruments) {
                MarketObservation observation = baseline.membership().get(instrument.getId());
                if (observation != null) {
                    references.put(instrument.getId(), observation);
                }
            }
            return references;
        }
        return observationService.latestAtOrBefore(
                currentInstruments.stream().map(Instrument::getId).toList(),
                baseline.checkedAt());
    }

    private static MembershipDelta membership(Baseline baseline, UUID instrumentId, Instant addedAt) {
        if (baseline == null) {
            return MembershipDelta.UNCHANGED;
        }
        if (baseline.membership() != null) {
            return baseline.membership().containsKey(instrumentId)
                    ? MembershipDelta.UNCHANGED
                    : MembershipDelta.ADDED;
        }
        if (addedAt != null && addedAt.isAfter(baseline.checkedAt())) {
            return MembershipDelta.ADDED;
        }
        return MembershipDelta.UNCHANGED;
    }

    private Baseline resolveBaseline(UUID watchlistId, String since) {
        if (since == null || since.isBlank()) {
            return checkRepository.findFirstByWatchlist_IdOrderByCheckedAtDescIdDesc(watchlistId)
                    .map(this::baselineFromCheck)
                    .orElse(null);
        }
        String trimmed = since.trim();
        try {
            UUID cursor = UUID.fromString(trimmed);
            WatchlistCheck check = checkRepository.findByIdAndWatchlist_Id(cursor, watchlistId)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, CURSOR_NOT_FOUND));
            return baselineFromCheck(check);
        } catch (IllegalArgumentException notACursor) {
            try {
                return new Baseline(null, Instant.parse(trimmed), null);
            } catch (DateTimeException invalidTimestamp) {
                throw new ApiException(HttpStatus.BAD_REQUEST, INVALID_SINCE);
            }
        }
    }

    private Baseline baselineFromCheck(WatchlistCheck check) {
        Map<UUID, MarketObservation> membership = new HashMap<>();
        for (WatchlistCheckItem item : checkItemRepository.findDetailedByCheckId(check.getId())) {
            membership.put(item.getInstrument().getId(), item.getObservation());
        }
        return new Baseline(check.getId(), check.getCheckedAt(), membership);
    }

    private Watchlist requireOwned(UUID userId, UUID watchlistId) {
        return watchlistRepository.findByIdAndUser_Id(watchlistId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, WATCHLIST_NOT_FOUND));
    }

    private static MarketSnapshot snapshot(Quote quote) {
        if (quote == null) {
            return null;
        }
        return new MarketSnapshot(
                quote.price(),
                quote.open(),
                quote.previousClose(),
                quote.high(),
                quote.low(),
                quote.volume(),
                quote.currency(),
                quote.sessionDate());
    }

    private static MarketSnapshot snapshot(MarketObservation observation) {
        if (observation == null) {
            return null;
        }
        return new MarketSnapshot(
                observation.getPrice(),
                observation.getOpenPrice(),
                observation.getPreviousClose(),
                observation.getDayHigh(),
                observation.getDayLow(),
                observation.getVolume(),
                observation.getCurrency(),
                observation.getSessionDate());
    }

    private static ChangeSummaryResponse summary(List<DetectedChange> changes, boolean hasBaseline) {
        List<String> highlights = new ArrayList<>(ChangeHighlights.from(changes));
        if (highlights.isEmpty()) {
            highlights.add(hasBaseline
                    ? "No meaningful change since the last check."
                    : "No earlier check is recorded.");
        }
        int instruments = (int) changes.stream().map(DetectedChange::instrumentId).distinct().count();
        return new ChangeSummaryResponse(
                changes.size(),
                instruments,
                ChangeHighlights.count(changes, ChangeType.PRICE_MOVE),
                ChangeHighlights.count(changes, ChangeType.VOLUME_SPIKE),
                ChangeHighlights.count(changes, ChangeType.NEW_DAY_HIGH),
                ChangeHighlights.count(changes, ChangeType.NEW_DAY_LOW),
                ChangeHighlights.count(changes, ChangeType.GAP_UP),
                ChangeHighlights.count(changes, ChangeType.GAP_DOWN),
                ChangeHighlights.count(changes, ChangeType.LARGE_INTRADAY_MOVE),
                ChangeHighlights.count(changes, ChangeType.WATCHLIST_ADDED),
                ChangeHighlights.count(changes, ChangeType.WATCHLIST_REMOVED),
                List.copyOf(highlights));
    }

    /**
     * @param membership null when the baseline is a timestamp rather than a saved check
     */
    private record Baseline(UUID cursor, Instant checkedAt, Map<UUID, MarketObservation> membership) {
    }
}
