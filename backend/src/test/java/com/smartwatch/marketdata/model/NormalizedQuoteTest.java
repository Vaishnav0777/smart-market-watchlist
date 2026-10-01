package com.smartwatch.marketdata.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class NormalizedQuoteTest {

    private static final Instant MARKET_TIME = Instant.parse("2024-06-03T10:00:00Z");
    private static final Instant SERVER_TIME = Instant.parse("2026-10-01T16:30:00Z");

    @Test
    void keepsTheProviderTimeWhenTheBackendObservesTheQuote() {
        Quote quote = quote(MarketDataQuality.REAL_TIME);

        Quote received = quote.observe(SERVER_TIME);

        assertThat(received.marketTimestamp()).isEqualTo(MARKET_TIME);
        assertThat(received.timestamp()).isEqualTo(MARKET_TIME);
        assertThat(received.observedAt()).isEqualTo(SERVER_TIME);
        assertThat(received.observedAt()).isNotEqualTo(received.marketTimestamp());
        assertThat(received.quality()).isEqualTo(MarketDataQuality.REAL_TIME);
        assertThat(received.source()).isEqualTo(MarketDataSource.MOCK);
        assertThat(quote.observedAt()).isNull();
    }

    @Test
    void representsDelayedAndStaleQuotesWithoutChangingThePrice() {
        Quote delayed = quote(MarketDataQuality.DELAYED).observe(SERVER_TIME);
        Quote stale = quote(MarketDataQuality.STALE).observe(SERVER_TIME);

        assertThat(delayed.quality()).isEqualTo(MarketDataQuality.DELAYED);
        assertThat(stale.quality()).isEqualTo(MarketDataQuality.STALE);
        assertThat(delayed.price()).isEqualByComparingTo(stale.price());
        assertThat(delayed.marketTimestamp()).isEqualTo(stale.marketTimestamp());
        assertThat(delayed.observedAt()).isEqualTo(SERVER_TIME);
        assertThat(stale.observedAt()).isNotEqualTo(stale.marketTimestamp());
    }

    private static Quote quote(MarketDataQuality quality) {
        return new Quote(
                "INFY",
                "Infosys",
                "NSE",
                "Information Technology",
                new BigDecimal("1800.25"),
                new BigDecimal("1785.00"),
                new BigDecimal("1790.00"),
                new BigDecimal("1810.00"),
                new BigDecimal("1788.00"),
                4_100_000L,
                MARKET_TIME,
                null,
                MarketDataSource.MOCK,
                quality,
                "INR",
                null,
                true);
    }
}
