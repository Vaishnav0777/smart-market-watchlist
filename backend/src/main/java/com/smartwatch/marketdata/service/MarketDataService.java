package com.smartwatch.marketdata.service;

import com.smartwatch.marketdata.model.HistoricalBar;
import com.smartwatch.marketdata.model.Quote;
import com.smartwatch.marketdata.provider.MarketDataProvider;
import org.springframework.stereotype.Service;

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

    public MarketDataService(MarketDataProvider marketDataProvider) {
        this.marketDataProvider = marketDataProvider;
    }

    public String source() {
        return marketDataProvider.source();
    }

    public Optional<Quote> getQuote(String symbol) {
        return marketDataProvider.getQuote(symbol);
    }

    public List<Quote> getQuotes(List<String> symbols) {
        return marketDataProvider.getQuotes(symbols);
    }

    public List<Quote> listQuotes() {
        return marketDataProvider.listQuotes();
    }

    public List<HistoricalBar> getHistoricalBars(String symbol, LocalDate from, LocalDate to) {
        return marketDataProvider.getHistoricalBars(symbol, from, to);
    }
}
