package com.smartwatch.marketdata.service;

import com.smartwatch.marketdata.model.HistoricalBar;
import com.smartwatch.marketdata.model.MarketDataSource;
import com.smartwatch.marketdata.model.Quote;
import com.smartwatch.marketdata.provider.MarketDataProvider;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Market-data application service.
 *
 * <p>Callers depend on this service, and the service depends on
 * {@link MarketDataProvider}. Replacing the mock provider does not change
 * this class.
 */
@Service
public class MarketDataService {

    private final MarketDataProvider marketDataProvider;
    private final Clock clock;

    public MarketDataService(MarketDataProvider marketDataProvider, Clock clock) {
        this.marketDataProvider = marketDataProvider;
        this.clock = clock;
    }

    public MarketDataSource source() {
        return marketDataProvider.source();
    }

    public Optional<Quote> getQuote(String symbol) {
        return marketDataProvider.getQuote(symbol).map(this::receive);
    }

    public List<Quote> getQuotes(List<String> symbols) {
        return marketDataProvider.getQuotes(symbols).stream().map(this::receive).toList();
    }

    public List<Quote> listQuotes() {
        return marketDataProvider.listQuotes().stream().map(this::receive).toList();
    }

    /**
     * Stamps when this backend received the quote. A provider that already set
     * {@code observedAt} keeps that acceptance time. The provider market time
     * is left unchanged.
     */
    private Quote receive(Quote quote) {
        if (quote.observedAt() != null) {
            return quote;
        }
        return quote.observe(clock.instant());
    }

    public List<HistoricalBar> getHistoricalBars(String symbol, LocalDate from, LocalDate to) {
        return marketDataProvider.getHistoricalBars(symbol, from, to);
    }
}
