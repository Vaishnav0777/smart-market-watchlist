package com.smartwatch.marketdata.dto;

/**
 * Identity plus the latest quote when the provider has one for the same
 * exchange and symbol. {@code quote} is null when it does not.
 */
public record InstrumentDetailResponse(
        InstrumentIdentityResponse instrument,
        MarketQuoteResponse quote) {
}
