package com.smartwatch.watchlist.service;

import com.smartwatch.common.web.ApiException;
import com.smartwatch.marketdata.entity.Instrument;
import com.smartwatch.marketdata.model.Quote;
import com.smartwatch.marketdata.repository.InstrumentRepository;
import com.smartwatch.marketdata.service.MarketDataService;
import com.smartwatch.user.entity.User;
import com.smartwatch.user.repository.UserRepository;
import com.smartwatch.watchlist.dto.QuoteObservationResponse;
import com.smartwatch.watchlist.dto.WatchlistDetailItemResponse;
import com.smartwatch.watchlist.dto.WatchlistDetailResponse;
import com.smartwatch.watchlist.dto.WatchlistItemResponse;
import com.smartwatch.watchlist.dto.WatchlistSummaryResponse;
import com.smartwatch.watchlist.entity.Watchlist;
import com.smartwatch.watchlist.entity.WatchlistItem;
import com.smartwatch.watchlist.repository.WatchlistItemOrderUpdate;
import com.smartwatch.watchlist.repository.WatchlistItemRepository;
import com.smartwatch.watchlist.repository.WatchlistItemRepository.WatchlistItemCount;
import com.smartwatch.watchlist.repository.WatchlistRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class WatchlistService {

    static final String WATCHLIST_NOT_FOUND = "Watchlist not found";
    static final String ITEM_NOT_FOUND = "Watchlist item not found";
    static final String INSTRUMENT_NOT_FOUND = "Instrument not found";
    static final String DUPLICATE_NAME = "A watchlist with this name already exists";
    static final String DUPLICATE_ITEM = "This instrument is already on the watchlist";
    static final String INVALID_REORDER = "Reorder must include each watchlist item exactly once";

    private final WatchlistRepository watchlistRepository;
    private final WatchlistItemRepository itemRepository;
    private final WatchlistItemOrderUpdate itemOrderUpdate;
    private final InstrumentRepository instrumentRepository;
    private final UserRepository userRepository;
    private final MarketDataService marketDataService;

    public WatchlistService(
            WatchlistRepository watchlistRepository,
            WatchlistItemRepository itemRepository,
            WatchlistItemOrderUpdate itemOrderUpdate,
            InstrumentRepository instrumentRepository,
            UserRepository userRepository,
            MarketDataService marketDataService) {
        this.watchlistRepository = watchlistRepository;
        this.itemRepository = itemRepository;
        this.itemOrderUpdate = itemOrderUpdate;
        this.instrumentRepository = instrumentRepository;
        this.userRepository = userRepository;
        this.marketDataService = marketDataService;
    }

    @Transactional
    public WatchlistSummaryResponse create(UUID userId, String name) {
        String normalized = normalizeName(name);
        if (watchlistRepository.existsByUser_IdAndName(userId, normalized)) {
            throw new ApiException(HttpStatus.CONFLICT, DUPLICATE_NAME);
        }
        User owner = userRepository.getReferenceById(userId);
        try {
            Watchlist watchlist = watchlistRepository.saveAndFlush(new Watchlist(owner, normalized));
            return WatchlistSummaryResponse.from(watchlist, 0);
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(HttpStatus.CONFLICT, DUPLICATE_NAME);
        }
    }

    @Transactional(readOnly = true)
    public List<WatchlistSummaryResponse> list(UUID userId) {
        Map<UUID, Long> counts = new HashMap<>();
        for (WatchlistItemCount count : itemRepository.countItemsForUser(userId)) {
            counts.put(count.getWatchlistId(), count.getItemCount());
        }
        return watchlistRepository.findByUser_IdOrderByCreatedAtAscIdAsc(userId).stream()
                .map(watchlist -> WatchlistSummaryResponse.from(
                        watchlist,
                        counts.getOrDefault(watchlist.getId(), 0L)))
                .toList();
    }

    /**
     * Reads persisted membership and attaches provider observations.
     *
     * <p>This method does not write a quote and does not move a "last checked"
     * cursor. A later change summary needs that cursor to stay put until the
     * user explicitly marks the list as checked.
     */
    @Transactional(readOnly = true)
    public WatchlistDetailResponse get(UUID userId, UUID watchlistId) {
        Watchlist watchlist = requireOwned(userId, watchlistId);
        List<WatchlistItem> items = itemRepository.findDetailedByWatchlistId(watchlistId);
        return toDetail(watchlist, items);
    }

    @Transactional
    public WatchlistSummaryResponse rename(UUID userId, UUID watchlistId, String name) {
        Watchlist watchlist = requireOwned(userId, watchlistId);
        String normalized = normalizeName(name);
        if (!watchlist.getName().equals(normalized)
                && watchlistRepository.existsByUser_IdAndName(userId, normalized)) {
            throw new ApiException(HttpStatus.CONFLICT, DUPLICATE_NAME);
        }
        watchlist.renameTo(normalized);
        try {
            watchlistRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(HttpStatus.CONFLICT, DUPLICATE_NAME);
        }
        return WatchlistSummaryResponse.from(watchlist, itemRepository.countByWatchlist_Id(watchlistId));
    }

    @Transactional
    public void delete(UUID userId, UUID watchlistId) {
        Watchlist watchlist = requireOwned(userId, watchlistId);
        UUID id = watchlist.getId();
        itemRepository.deleteByWatchlistId(id);
        watchlistRepository.deleteById(id);
    }

    @Transactional
    public WatchlistItemResponse addItem(UUID userId, UUID watchlistId, UUID instrumentId) {
        Watchlist watchlist = requireOwned(userId, watchlistId);
        Instrument instrument = instrumentRepository.findById(instrumentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, INSTRUMENT_NOT_FOUND));
        if (itemRepository.existsByWatchlist_IdAndInstrument_Id(watchlistId, instrumentId)) {
            throw new ApiException(HttpStatus.CONFLICT, DUPLICATE_ITEM);
        }
        Integer maxSortOrder = itemRepository.findMaxSortOrder(watchlistId);
        int sortOrder = maxSortOrder == null ? 0 : maxSortOrder + 1;
        try {
            WatchlistItem item = itemRepository.saveAndFlush(new WatchlistItem(watchlist, instrument, sortOrder));
            return WatchlistItemResponse.from(item);
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(HttpStatus.CONFLICT, DUPLICATE_ITEM);
        }
    }

    @Transactional
    public void removeItem(UUID userId, UUID watchlistId, UUID itemId) {
        requireOwned(userId, watchlistId);
        WatchlistItem item = itemRepository.findByIdAndWatchlist_Id(itemId, watchlistId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ITEM_NOT_FOUND));
        itemRepository.delete(item);
    }

    @Transactional
    public void reorder(UUID userId, UUID watchlistId, List<UUID> itemIds) {
        requireOwned(userId, watchlistId);
        List<UUID> currentIds = itemRepository.findIdsByWatchlistId(watchlistId);
        if (!isPermutation(currentIds, itemIds)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, INVALID_REORDER);
        }
        if (itemIds.isEmpty()) {
            return;
        }
        int updated = itemOrderUpdate.apply(watchlistId, itemIds);
        if (updated != itemIds.size()) {
            throw new ApiException(HttpStatus.CONFLICT, "Watchlist items changed; retry the reorder");
        }
    }

    private WatchlistDetailResponse toDetail(Watchlist watchlist, List<WatchlistItem> items) {
        Map<String, Quote> quotes = quotesFor(items);
        List<WatchlistDetailItemResponse> responses = items.stream()
                .map(item -> {
                    WatchlistItemResponse persisted = WatchlistItemResponse.from(item);
                    Quote quote = quotes.get(quoteKey(item.getInstrument().getExchange(), item.getInstrument().getSymbol()));
                    QuoteObservationResponse observation = quote == null ? null : QuoteObservationResponse.from(quote);
                    return WatchlistDetailItemResponse.of(persisted, observation);
                })
                .toList();
        return new WatchlistDetailResponse(
                watchlist.getId(),
                watchlist.getName(),
                watchlist.getCreatedAt(),
                watchlist.getUpdatedAt(),
                responses);
    }

    private Map<String, Quote> quotesFor(List<WatchlistItem> items) {
        List<String> symbols = items.stream()
                .map(item -> item.getInstrument().getSymbol())
                .distinct()
                .toList();
        if (symbols.isEmpty()) {
            return Map.of();
        }
        Map<String, Quote> quotes = new HashMap<>();
        for (Quote quote : marketDataService.getQuotes(symbols)) {
            quotes.putIfAbsent(quoteKey(quote.exchange(), quote.symbol()), quote);
        }
        return quotes;
    }

    private Watchlist requireOwned(UUID userId, UUID watchlistId) {
        return watchlistRepository.findByIdAndUser_Id(watchlistId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, WATCHLIST_NOT_FOUND));
    }

    private static boolean isPermutation(List<UUID> currentIds, List<UUID> requestedIds) {
        if (requestedIds.size() != currentIds.size() || new HashSet<>(requestedIds).size() != requestedIds.size()) {
            return false;
        }
        return new HashSet<>(currentIds).containsAll(requestedIds);
    }

    private static String normalizeName(String name) {
        if (name == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Watchlist name must be between 1 and 120 characters");
        }
        String trimmed = name.trim();
        if (trimmed.isEmpty() || trimmed.length() > 120) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Watchlist name must be between 1 and 120 characters");
        }
        return trimmed;
    }

    private static String quoteKey(String exchange, String symbol) {
        return exchange + "\n" + symbol;
    }
}
