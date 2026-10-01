package com.smartwatch.marketdata.provider.upstox;

import com.smartwatch.marketdata.model.HistoricalBar;
import com.smartwatch.marketdata.model.MarketDataSource;
import com.smartwatch.marketdata.model.Quote;
import com.smartwatch.marketdata.provider.MarketDataProvider;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Upstox V3 full market quotes over REST.
 *
 * <p>Quotes are requested with the stored external instrument key, not with
 * the internal symbol. {@code marketTimestamp} is the last trade.
 * {@code observedAt} is this process's clock after that response has been
 * accepted. The Upstox {@code timestamp} field is the snapshot time and is
 * used only to judge freshness. Historical bars and WebSocket streaming are
 * not part of this provider.
 */
public final class UpstoxMarketDataProvider implements MarketDataProvider {

    static final int MAX_KEYS_PER_REQUEST = 500;

    private final UpstoxInstrumentKeySource instrumentKeys;
    private final UpstoxMarketQuoteClient client;
    private final Clock clock;
    private final Duration staleAfter;
    private final String accessToken;

    public UpstoxMarketDataProvider(
            UpstoxInstrumentKeySource instrumentKeys,
            UpstoxMarketQuoteClient client,
            Clock clock,
            Duration staleAfter,
            String accessToken) {
        this.instrumentKeys = instrumentKeys;
        this.client = client;
        this.clock = clock;
        this.staleAfter = staleAfter == null ? UpstoxProperties.DEFAULT_STALE_AFTER : staleAfter;
        this.accessToken = accessToken == null ? "" : accessToken;
    }

    @Override
    public MarketDataSource source() {
        return MarketDataSource.UPSTOX;
    }

    @Override
    public Optional<Quote> getQuote(String symbol) {
        requireToken();
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol must not be blank");
        }
        List<Quote> quotes = quotesFor(instrumentKeys.findForSymbols(List.of(symbol)));
        String normalized = normalize(symbol);
        return quotes.stream()
                .filter(quote -> quote.symbol().equalsIgnoreCase(normalized))
                .findFirst();
    }

    @Override
    public List<Quote> getQuotes(List<String> symbols) {
        requireToken();
        if (symbols == null) {
            throw new IllegalArgumentException("symbols must not be null");
        }
        if (symbols.isEmpty()) {
            return List.of();
        }
        return quotesFor(instrumentKeys.findForSymbols(symbols));
    }

    @Override
    public List<Quote> listQuotes() {
        requireToken();
        return quotesFor(instrumentKeys.findAll());
    }

    /**
     * This REST quote provider does not supply historical bars. An empty list
     * means no bars were requested from Upstox, not that a session had no trades.
     */
    @Override
    public List<HistoricalBar> getHistoricalBars(String symbol, LocalDate from, LocalDate to) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol must not be blank");
        }
        if (from == null || to == null) {
            throw new IllegalArgumentException("from and to must not be null");
        }
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("from must not be after to");
        }
        return List.of();
    }

    private List<Quote> quotesFor(List<UpstoxInstrumentMapping> mappings) {
        if (mappings == null || mappings.isEmpty()) {
            return List.of();
        }
        Map<String, UpstoxInstrumentMapping> byKey = new LinkedHashMap<>();
        for (UpstoxInstrumentMapping mapping : mappings) {
            if (mapping.externalInstrumentKey() == null || mapping.externalInstrumentKey().isBlank()) {
                continue;
            }
            byKey.putIfAbsent(mapping.externalInstrumentKey(), mapping);
        }
        if (byKey.isEmpty()) {
            return List.of();
        }
        List<UpstoxInstrumentMapping> requested = List.copyOf(byKey.values());
        List<Quote> quotes = new ArrayList<>();
        for (int offset = 0; offset < requested.size(); offset += MAX_KEYS_PER_REQUEST) {
            List<UpstoxInstrumentMapping> batch = requested.subList(
                    offset, Math.min(offset + MAX_KEYS_PER_REQUEST, requested.size()));
            List<String> keys = batch.stream().map(UpstoxInstrumentMapping::externalInstrumentKey).toList();
            String body = client.fetchQuotes(keys);
            quotes.addAll(UpstoxQuoteParser.parse(body, batch, clock, staleAfter));
        }
        return List.copyOf(quotes);
    }

    private void requireToken() {
        if (accessToken.isBlank()) {
            throw MarketDataUnavailableException.notConfigured();
        }
    }

    private static String normalize(String symbol) {
        return symbol.trim().toUpperCase(Locale.ROOT);
    }
}
