package com.smartwatch.portfolio.dto;

import com.smartwatch.marketdata.model.MarketDataQuality;
import com.smartwatch.marketdata.model.MarketDataSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One stored holding with its own result. A missing quote leaves the market
 * fields empty. This is not a forecast.
 */
public record HoldingAnalyticsResponse(
        UUID instrumentId,
        String symbol,
        String exchange,
        String sector,
        String currency,
        BigDecimal invested,
        BigDecimal currentValue,
        BigDecimal pnl,
        BigDecimal returnPercent,
        MarketDataQuality quality,
        MarketDataSource source,
        Instant marketTimestamp,
        Instant observedAt) {
}
