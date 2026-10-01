package com.smartwatch.change;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Figures used to compare one moment in the market.
 * Identity of the instrument is kept outside this snapshot.
 */
public record MarketSnapshot(
        BigDecimal price,
        BigDecimal open,
        BigDecimal previousClose,
        BigDecimal dayHigh,
        BigDecimal dayLow,
        Long volume,
        String currency,
        LocalDate sessionDate) {
}
