package com.smartwatch.marketdata.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One daily bar for an instrument.
 *
 * <p>{@code synthetic} is true when the bar is a development fixture.
 */
public record HistoricalBar(
        String symbol,
        LocalDate date,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        long volume,
        boolean synthetic) {
}
