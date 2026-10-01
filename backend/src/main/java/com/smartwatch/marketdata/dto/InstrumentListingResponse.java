package com.smartwatch.marketdata.dto;

public record InstrumentListingResponse(
        InstrumentIdentityResponse instrument,
        MarketQuoteResponse quote) {
}
