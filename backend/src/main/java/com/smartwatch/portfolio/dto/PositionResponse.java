package com.smartwatch.portfolio.dto;

import com.smartwatch.marketdata.dto.InstrumentIdentityResponse;
import com.smartwatch.marketdata.dto.MarketQuoteResponse;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A stored holding. {@code quote} is the latest provider observation when
 * the exchange and symbol match. Value and return are not stored here.
 */
public record PositionResponse(
        UUID id,
        BigDecimal quantity,
        BigDecimal averageBuyPrice,
        InstrumentIdentityResponse instrument,
        MarketQuoteResponse quote) {
}
