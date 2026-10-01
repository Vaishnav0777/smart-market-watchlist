package com.smartwatch.watchlist.dto;

import com.smartwatch.marketdata.model.MarketDataQuality;
import com.smartwatch.marketdata.model.MarketDataSource;
import com.smartwatch.marketdata.model.Quote;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A market observation attached to a read response.
 *
 * <p>This value is not stored on the watchlist item. {@code synthetic} stays
 * visible so a fixture cannot be mistaken for a live price.
 */
public record QuoteObservationResponse(
        String symbol,
        String exchange,
        BigDecimal price,
        BigDecimal previousClose,
        Instant marketTimestamp,
        Instant observedAt,
        MarketDataSource source,
        MarketDataQuality quality,
        Instant timestamp,
        boolean synthetic) {

    public static QuoteObservationResponse from(Quote quote) {
        return new QuoteObservationResponse(
                quote.symbol(),
                quote.exchange(),
                quote.price(),
                quote.previousClose(),
                quote.marketTimestamp(),
                quote.observedAt(),
                quote.source(),
                quote.quality(),
                quote.marketTimestamp(),
                quote.synthetic());
    }
}
