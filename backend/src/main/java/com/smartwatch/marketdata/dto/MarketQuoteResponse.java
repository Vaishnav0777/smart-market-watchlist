package com.smartwatch.marketdata.dto;

import com.smartwatch.marketdata.model.Quote;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * A quote returned by a read endpoint. It is not stored by that read.
 * {@code synthetic} stays visible so a fixture cannot be mistaken for a live price.
 */
public record MarketQuoteResponse(
        String symbol,
        String companyName,
        String exchange,
        String sector,
        BigDecimal price,
        BigDecimal previousClose,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        long volume,
        Instant timestamp,
        String currency,
        LocalDate sessionDate,
        boolean synthetic) {

    public static MarketQuoteResponse from(Quote quote) {
        return new MarketQuoteResponse(
                quote.symbol(),
                quote.companyName(),
                quote.exchange(),
                quote.sector(),
                quote.price(),
                quote.previousClose(),
                quote.open(),
                quote.high(),
                quote.low(),
                quote.volume(),
                quote.timestamp(),
                quote.currency(),
                quote.sessionDate(),
                quote.synthetic());
    }
}
