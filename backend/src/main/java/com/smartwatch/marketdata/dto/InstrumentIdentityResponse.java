package com.smartwatch.marketdata.dto;

import com.smartwatch.marketdata.entity.Instrument;

import java.util.UUID;

/**
 * Persisted instrument identity. This is not a quote.
 */
public record InstrumentIdentityResponse(
        UUID id,
        String symbol,
        String displayName,
        String exchange,
        String sector,
        String instrumentType) {

    public static InstrumentIdentityResponse from(Instrument instrument) {
        return new InstrumentIdentityResponse(
                instrument.getId(),
                instrument.getSymbol(),
                instrument.getCompanyName(),
                instrument.getExchange(),
                instrument.getSector(),
                instrument.getInstrumentType().name());
    }
}
