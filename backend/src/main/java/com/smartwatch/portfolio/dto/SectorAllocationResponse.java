package com.smartwatch.portfolio.dto;

import java.math.BigDecimal;

/**
 * Share of the amount invested in one persisted sector.
 */
public record SectorAllocationResponse(
        String sector,
        BigDecimal invested,
        BigDecimal percentage) {
}
