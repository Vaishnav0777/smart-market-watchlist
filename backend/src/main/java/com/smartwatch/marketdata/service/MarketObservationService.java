package com.smartwatch.marketdata.service;

import com.smartwatch.marketdata.entity.Instrument;
import com.smartwatch.marketdata.entity.MarketObservation;
import com.smartwatch.marketdata.model.Quote;
import com.smartwatch.marketdata.repository.MarketObservationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Reads quotes through {@link MarketDataService} and stores an observation
 * only when a caller asks to record one.
 */
@Service
public class MarketObservationService {

    private final MarketDataService marketDataService;
    private final MarketObservationRepository observationRepository;

    public MarketObservationService(
            MarketDataService marketDataService,
            MarketObservationRepository observationRepository) {
        this.marketDataService = marketDataService;
        this.observationRepository = observationRepository;
    }

    public Map<UUID, Quote> latestQuotes(List<Instrument> instruments) {
        if (instruments.isEmpty()) {
            return Map.of();
        }
        List<String> symbols = instruments.stream().map(Instrument::getSymbol).distinct().toList();
        Map<String, Quote> byListing = new HashMap<>();
        for (Quote quote : marketDataService.getQuotes(symbols)) {
            byListing.putIfAbsent(key(quote.exchange(), quote.symbol()), quote);
        }
        Map<UUID, Quote> quotes = new LinkedHashMap<>();
        for (Instrument instrument : instruments) {
            Quote quote = byListing.get(key(instrument.getExchange(), instrument.getSymbol()));
            if (quote != null) {
                quotes.put(instrument.getId(), quote);
            }
        }
        return quotes;
    }

    @Transactional
    public Map<UUID, MarketObservation> recordLatest(List<Instrument> instruments) {
        Map<UUID, Quote> quotes = latestQuotes(instruments);
        Map<UUID, MarketObservation> recorded = new LinkedHashMap<>();
        for (Instrument instrument : instruments) {
            Quote quote = quotes.get(instrument.getId());
            if (!canRecord(quote)) {
                continue;
            }
            MarketObservation observation = observationRepository.save(new MarketObservation(
                    instrument,
                    quote.source(),
                    quote.observedAt(),
                    quote.marketTimestamp(),
                    quote.quality(),
                    quote.price(),
                    quote.previousClose(),
                    quote.open(),
                    quote.high(),
                    quote.low(),
                    quote.volume(),
                    quote.currency(),
                    quote.sessionDate()));
            recorded.put(instrument.getId(), observation);
        }
        return recorded;
    }

    @Transactional(readOnly = true)
    public Map<UUID, MarketObservation> latestAtOrBefore(Collection<UUID> instrumentIds, Instant at) {
        if (instrumentIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, MarketObservation> latest = new HashMap<>();
        for (MarketObservation observation : observationRepository
                .findByInstrument_IdInAndObservedAtLessThanEqualOrderByObservedAtDescIdDesc(instrumentIds, at)) {
            latest.putIfAbsent(observation.getInstrument().getId(), observation);
        }
        return latest;
    }

    private static boolean canRecord(Quote quote) {
        return quote != null
                && quote.price() != null
                && quote.marketTimestamp() != null
                && quote.observedAt() != null
                && quote.source() != null
                && quote.quality() != null
                && quote.currency() != null
                && !quote.currency().isBlank();
    }

    private static String key(String exchange, String symbol) {
        return exchange + "\n" + symbol;
    }
}
