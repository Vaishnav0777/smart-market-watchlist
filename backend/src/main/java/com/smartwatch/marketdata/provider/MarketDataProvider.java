package com.smartwatch.marketdata.provider;

import com.smartwatch.marketdata.model.HistoricalBar;
import com.smartwatch.marketdata.model.Quote;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Port for market data.
 *
 * <p>Application code depends on this interface. {@code MockMarketDataProvider}
 * is the only implementation today. A licensed provider can be added later
 * behind the same methods without changing callers.
 */
public interface MarketDataProvider {

    Optional<Quote> getQuote(String symbol);

    List<Quote> getQuotes(List<String> symbols);

    List<HistoricalBar> getHistoricalBars(String symbol, LocalDate from, LocalDate to);
}
