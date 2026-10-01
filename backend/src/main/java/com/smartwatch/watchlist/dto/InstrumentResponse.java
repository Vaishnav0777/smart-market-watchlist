package com.smartwatch.watchlist.dto;

import com.smartwatch.marketdata.entity.Instrument;

import java.util.UUID;

/**
 * Persisted instrument identity. This is not a quote.
 */
public record InstrumentResponse(
        UUID id,
        String symbol,
        String displayName,
        String exchange,
        String sector,
        String instrumentType) {

    public static InstrumentResponse from(Instrument instrument) {
        return new InstrumentResponse(
                instrument.getId(),
                instrument.getSymbol(),
                instrument.getCompanyName(),
                instrument.getExchange(),
                instrument.getSector(),
                instrument.getInstrumentType().name());
    }
}
