package com.smartwatch.portfolio.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Share of current market value in one instrument. Holdings without a quote are omitted.
 */
public record InstrumentAllocationResponse(
        UUID instrumentId,
        String symbol,
        String exchange,
        String sector,
        BigDecimal currentValue,
        BigDecimal percentage) {
}
