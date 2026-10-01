package com.smartwatch.marketdata.provider.upstox;

/**
 * One internal instrument and the Upstox key used to request its quote.
 */
public record UpstoxInstrumentMapping(
        String symbol,
        String companyName,
        String exchange,
        String sector,
        String externalInstrumentKey) {
}
