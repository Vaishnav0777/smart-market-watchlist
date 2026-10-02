package com.smartwatch.portfolio.dto;

import java.math.BigDecimal;

/**
 * Share of current market value in one persisted sector. Holdings without a quote are omitted.
 */
public record SectorAllocationResponse(
        String sector,
        BigDecimal currentValue,
        BigDecimal percentage) {
}
