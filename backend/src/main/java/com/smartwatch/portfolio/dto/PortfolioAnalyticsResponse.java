package com.smartwatch.portfolio.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Read-only analytics for one portfolio. Combined monetary totals and allocation
 * percentages are present only when every valued quote uses the same currency.
 * Allocation weights use current market value, not invested amount.
 * {@code realTime} is true only when every valued quote has quality {@code REAL_TIME}.
 */
public record PortfolioAnalyticsResponse(
        UUID portfolioId,
        String currency,
        boolean mixedCurrencies,
        boolean realTime,
        BigDecimal totalInvested,
        BigDecimal currentValue,
        BigDecimal totalPnl,
        BigDecimal returnPercent,
        int winners,
        int losers,
        int unvaluedPositions,
        HoldingAnalyticsResponse best,
        HoldingAnalyticsResponse worst,
        List<SectorAllocationResponse> sectorAllocations,
        List<InstrumentAllocationResponse> instrumentAllocations,
        List<HoldingAnalyticsResponse> holdings) {
}
