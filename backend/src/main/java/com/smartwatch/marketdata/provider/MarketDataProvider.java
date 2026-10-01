package com.smartwatch.marketdata.provider;

import com.smartwatch.marketdata.model.HistoricalBar;
import com.smartwatch.marketdata.model.MarketDataSource;
import com.smartwatch.marketdata.model.Quote;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Port for market data.
 *
 * <p>Application code depends on this interface. {@code MockMarketDataProvider}
 * is the default. {@code UpstoxMarketDataProvider} is used only when Upstox
 * is enabled and an access token is configured.
 */
public interface MarketDataProvider {

    /**
     * Stable id of this provider, stored on observations. Not a secret.
     */
    MarketDataSource source();

    Optional<Quote> getQuote(String symbol);

    List<Quote> getQuotes(List<String> symbols);

    /**
     * Every quote this provider can currently identify, in a stable order.
     * This is a read of the provider catalog, not a persisted observation.
     */
    List<Quote> listQuotes();

    List<HistoricalBar> getHistoricalBars(String symbol, LocalDate from, LocalDate to);
}
