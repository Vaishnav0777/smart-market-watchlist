package com.smartwatch.marketdata.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * A price snapshot for one instrument.
 *
 * <p>{@code marketTimestamp} is when the provider says the quote was updated.
 * {@code observedAt} is when this backend received it. One is never copied
 * onto the other. {@code observedAt} is null until the backend stamps it.
 * {@code synthetic} stays true for development fixtures.
 */
public record Quote(
        String symbol,
        String companyName,
        String exchange,
        String sector,
        BigDecimal price,
        BigDecimal previousClose,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        long volume,
        Instant marketTimestamp,
        Instant observedAt,
        MarketDataSource source,
        MarketDataQuality quality,
        String currency,
        LocalDate sessionDate,
        boolean synthetic) {

    /**
     * Provider market time. This is not the server observation time.
     */
    public Instant timestamp() {
        return marketTimestamp;
    }

    /**
     * Records when this backend received the quote. The provider time stays as it was.
     */
    public Quote observe(Instant serverObservedAt) {
        if (serverObservedAt == null) {
            throw new IllegalArgumentException("server observation time is required");
        }
        return new Quote(
                symbol,
                companyName,
                exchange,
                sector,
                price,
                previousClose,
                open,
                high,
                low,
                volume,
                marketTimestamp,
                serverObservedAt,
                source,
                quality,
                currency,
                sessionDate,
                synthetic);
    }
}
