package com.smartwatch.marketdata.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * A price snapshot for one instrument.
 *
 * <p>{@code synthetic} is true when the figures are development fixtures.
 * Callers must keep that distinction visible to users.
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
        Instant timestamp,
        String currency,
        LocalDate sessionDate,
        boolean synthetic) {
}
