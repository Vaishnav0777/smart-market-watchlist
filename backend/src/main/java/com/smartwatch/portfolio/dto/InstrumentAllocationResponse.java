package com.smartwatch.portfolio.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Share of the amount invested in one instrument.
 */
public record InstrumentAllocationResponse(
        UUID instrumentId,
        String symbol,
        String exchange,
        String sector,
        BigDecimal invested,
        BigDecimal percentage) {
}
