package com.smartwatch.marketdata.provider.upstox;

import com.smartwatch.marketdata.model.MarketDataQuality;
import com.smartwatch.marketdata.model.MarketDataSource;
import com.smartwatch.marketdata.model.Quote;
import com.smartwatch.marketdata.provider.MockMarketDataProvider;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UpstoxMarketDataProviderTest {

    private static final Instant LAST_TRADE = Instant.parse("2024-06-03T10:00:00Z");
    private static final Instant SNAPSHOT = Instant.parse("2024-06-03T10:05:00Z");
    private static final Instant OBSERVED = Instant.parse("2024-06-03T10:01:00Z");
    private static final String RELIANCE_KEY = "NSE_EQ|INE002A01018";
    private static final String TCS_KEY = "NSE_EQ|INE467B01029";
    private static final Duration STALE_AFTER = Duration.ofMinutes(15);

    private final FakeClient client = new FakeClient();
    private final MemoryKeys keys = new MemoryKeys(List.of(
            new UpstoxInstrumentMapping("RELIANCE", "Reliance Industries", "NSE", "Energy", RELIANCE_KEY),
            new UpstoxInstrumentMapping("TCS", "Tata Consultancy Services", "NSE", "Information Technology", TCS_KEY)));

    @Test
    void mapsASuccessfulQuote() {
        client.body = payload(quote("RELIANCE", RELIANCE_KEY, "2500.75", "2488.10", "2480.50", "2520.00", "2470.25", 3_200_000L, 10L, LAST_TRADE));
        UpstoxMarketDataProvider provider = provider(OBSERVED);

        Quote quote = provider.getQuote("RELIANCE").orElseThrow();

        assertThat(quote.symbol()).isEqualTo("RELIANCE");
        assertThat(quote.companyName()).isEqualTo("Reliance Industries");
        assertThat(quote.exchange()).isEqualTo("NSE");
        assertThat(quote.price()).isEqualByComparingTo("2500.75");
        assertThat(quote.previousClose()).isEqualByComparingTo("2488.10");
        assertThat(quote.open()).isEqualByComparingTo("2480.50");
        assertThat(quote.high()).isEqualByComparingTo("2520.00");
        assertThat(quote.low()).isEqualByComparingTo("2470.25");
        assertThat(quote.volume()).isEqualTo(3_200_000L);
        assertThat(quote.marketTimestamp()).isEqualTo(LAST_TRADE);
        assertThat(quote.timestamp()).isEqualTo(quote.marketTimestamp());
        assertThat(quote.marketTimestamp()).isNotEqualTo(SNAPSHOT);
        assertThat(quote.observedAt()).isEqualTo(OBSERVED);
        assertThat(quote.observedAt()).isNotEqualTo(quote.marketTimestamp());
        assertThat(quote.source()).isEqualTo(MarketDataSource.UPSTOX);
        assertThat(quote.quality()).isEqualTo(MarketDataQuality.REAL_TIME);
        assertThat(quote.currency()).isEqualTo("INR");
        assertThat(quote.synthetic()).isFalse();
        assertThat(quote.sessionDate()).isEqualTo(LAST_TRADE.atZone(UpstoxQuoteParser.MARKET_ZONE).toLocalDate());
    }

    @Test
    void requestsSeveralInstrumentsOnce() {
        client.body = payload(
                quote("RELIANCE", RELIANCE_KEY, "2500.00", "2480.00", "2490.00", "2510.00", "2475.00", 3_200_000L, 1L, LAST_TRADE),
                quote("TCS", TCS_KEY, "4000.50", "3980.00", "3990.00", "4010.00", "3970.00", 1_500_000L, 2L, LAST_TRADE));

        List<Quote> quotes = provider(OBSERVED).getQuotes(List.of("RELIANCE", "TCS"));

        assertThat(client.calls).containsExactly(List.of(RELIANCE_KEY, TCS_KEY));
        assertThat(quotes).extracting(Quote::symbol).containsExactly("RELIANCE", "TCS");
        assertThat(quotes).allSatisfy(quote -> {
            assertThat(quote.source()).isEqualTo(MarketDataSource.UPSTOX);
            assertThat(quote.previousClose()).isNotNull();
            assertThat(quote.open()).isNotNull();
            assertThat(quote.volume()).isPositive();
        });
    }

    @Test
    void stampsObservedAtOnlyAfterTheResponseIsAccepted() {
        client.body = payload(quote(
                "RELIANCE", RELIANCE_KEY, "2500.00", "2480.00", "2490.00", "2510.00", "2475.00", 100L, 1L, LAST_TRADE));
        boolean[] responseAccepted = {false};
        client.whenResponseReady = () -> responseAccepted[0] = true;
        Clock clock = new Clock() {
            @Override
            public Instant instant() {
                assertThat(responseAccepted[0]).isTrue();
                return OBSERVED;
            }

            @Override
            public ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return this;
            }
        };

        Quote quote = new UpstoxMarketDataProvider(
                keys, client, clock, STALE_AFTER, "fixture-token-not-real").getQuote("RELIANCE").orElseThrow();

        assertThat(quote.observedAt()).isEqualTo(OBSERVED);
        assertThat(quote.marketTimestamp()).isEqualTo(LAST_TRADE);
        assertThat(quote.observedAt()).isNotEqualTo(quote.marketTimestamp());
        assertThat(quote.source()).isEqualTo(MarketDataSource.UPSTOX);
    }

    @Test
    void keepsAFreshSnapshotWhenTheLastTradeIsOld() {
        Instant observed = Instant.parse("2026-10-01T16:30:00Z");
        Instant lastTrade = observed.minus(Duration.ofHours(2));
        Instant snapshot = observed.minus(Duration.ofMinutes(1));
        client.body = payload(quote(
                "RELIANCE", RELIANCE_KEY, "2500.00", "2480.00", "2490.00", "2510.00", "2475.00",
                100L, 1L, lastTrade, snapshot));

        Quote quote = provider(observed).getQuote("RELIANCE").orElseThrow();

        assertThat(quote.quality()).isEqualTo(MarketDataQuality.REAL_TIME);
        assertThat(quote.marketTimestamp()).isEqualTo(lastTrade);
        assertThat(quote.observedAt()).isEqualTo(observed);
        assertThat(quote.marketTimestamp()).isNotEqualTo(quote.observedAt());
        assertThat(quote.marketTimestamp()).isNotEqualTo(snapshot);
        assertThat(quote.source()).isEqualTo(MarketDataSource.UPSTOX);
    }

    @Test
    void marksAnOldProviderSnapshotStale() {
        Instant observed = Instant.parse("2026-10-01T16:30:00Z");
        Instant lastTrade = observed.minus(Duration.ofMinutes(1));
        Instant snapshot = observed.minus(Duration.ofMinutes(16));
        client.body = payload(quote(
                "RELIANCE", RELIANCE_KEY, "2500.00", "2480.00", "2490.00", "2510.00", "2475.00",
                100L, 1L, lastTrade, snapshot));

        Quote quote = provider(observed).getQuote("RELIANCE").orElseThrow();

        assertThat(quote.quality()).isEqualTo(MarketDataQuality.STALE);
        assertThat(quote.marketTimestamp()).isEqualTo(lastTrade);
        assertThat(quote.observedAt()).isEqualTo(observed);
        assertThat(quote.marketTimestamp()).isNotEqualTo(snapshot);
        assertThat(quote.observedAt()).isNotEqualTo(quote.marketTimestamp());
        assertThat(quote.source()).isEqualTo(MarketDataSource.UPSTOX);
    }

    @Test
    void omitsAQuoteWhenTheLastTradeTimeIsMissing() {
        client.body = """
                {"status":"success","data":{"NSE_EQ:RELIANCE":{
                  "instrument_token":"%s","symbol":"RELIANCE","last_price":2500.00,"volume":10,
                  "timestamp":"2024-06-03T15:30:00+05:30","ohlc":{"open":1,"high":2,"low":0.5,"close":9}
                }}}
                """.formatted(RELIANCE_KEY);

        assertThat(provider(OBSERVED).getQuote("RELIANCE")).isEmpty();
    }

    @Test
    void rejectsABlankTokenWithoutCallingTheProvider() {
        UpstoxMarketDataProvider provider = new UpstoxMarketDataProvider(
                keys, client, Clock.fixed(OBSERVED, ZoneOffset.UTC), STALE_AFTER, "  ");

        assertThatThrownBy(() -> provider.getQuote("RELIANCE"))
                .isInstanceOf(MarketDataUnavailableException.class)
                .extracting(exception -> ((MarketDataUnavailableException) exception).getReason())
                .isEqualTo(MarketDataUnavailableException.Reason.NOT_CONFIGURED);
        assertThat(client.calls).isEmpty();
        assertThatThrownBy(() -> provider.getQuote("RELIANCE"))
                .hasMessageNotContaining("Bearer");
    }

    @Test
    void doesNotTurnAProviderFailureIntoAQuote() {
        client.error = MarketDataUnavailableException.unavailable();

        assertThatThrownBy(() -> provider(OBSERVED).getQuotes(List.of("RELIANCE")))
                .isInstanceOf(MarketDataUnavailableException.class)
                .extracting(exception -> ((MarketDataUnavailableException) exception).getReason())
                .isEqualTo(MarketDataUnavailableException.Reason.UNAVAILABLE);
    }

    @Test
    void rejectsAMalformedBody() {
        client.body = "{";

        assertThatThrownBy(() -> provider(OBSERVED).getQuote("RELIANCE"))
                .isInstanceOf(MarketDataUnavailableException.class)
                .extracting(exception -> ((MarketDataUnavailableException) exception).getReason())
                .isEqualTo(MarketDataUnavailableException.Reason.INVALID_RESPONSE);
    }

    @Test
    void omitsAnInstrumentTheProviderDidNotReturn() {
        client.body = payload(quote("RELIANCE", RELIANCE_KEY, "2500.00", "2480.00", "2490.00", "2510.00", "2475.00", 10L, 1L, LAST_TRADE));

        List<Quote> quotes = provider(OBSERVED).getQuotes(List.of("RELIANCE", "TCS"));

        assertThat(quotes).extracting(Quote::symbol).containsExactly("RELIANCE");
        assertThat(provider(OBSERVED).getQuote("TCS")).isEmpty();
    }

    @Test
    void omitsASymbolWithNoStoredKey() {
        assertThat(provider(OBSERVED).getQuote("INFY")).isEmpty();
        assertThat(client.calls).isEmpty();
    }

    @Test
    void mockProviderStillReturnsTheFrozenCatalog() {
        Quote quote = new MockMarketDataProvider().getQuote("RELIANCE").orElseThrow();

        assertThat(quote.source()).isEqualTo(MarketDataSource.MOCK);
        assertThat(quote.synthetic()).isTrue();
        assertThat(quote.quality()).isEqualTo(MarketDataQuality.END_OF_DAY);
    }

    @Test
    void upstoxStaysDisabledUnlessConfigured() {
        UpstoxProperties properties = new UpstoxProperties();

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.tokenConfigured()).isFalse();
        assertThat(properties.getStaleAfter()).isEqualTo(Duration.ofMinutes(15));
    }

    private UpstoxMarketDataProvider provider(Instant observedAt) {
        return new UpstoxMarketDataProvider(
                keys,
                client,
                Clock.fixed(observedAt, ZoneOffset.UTC),
                STALE_AFTER,
                "fixture-token-not-real");
    }

    private static String payload(String... quotes) {
        return "{\"status\":\"success\",\"data\":{" + String.join(",", quotes) + "}}";
    }

    private static String quote(
            String symbol,
            String key,
            String lastPrice,
            String previousClose,
            String open,
            String high,
            String low,
            long volume,
            long candleVolume,
            Instant lastTrade) {
        return quote(symbol, key, lastPrice, previousClose, open, high, low, volume, candleVolume, lastTrade, null);
    }

    private static String quote(
            String symbol,
            String key,
            String lastPrice,
            String previousClose,
            String open,
            String high,
            String low,
            long volume,
            long candleVolume,
            Instant lastTrade,
            Instant snapshot) {
        String snapshotText = snapshot == null ? "2024-06-03T15:35:00+05:30" : snapshot.toString();
        return """
                "%s:%s":{
                  "instrument_token":"%s",
                  "symbol":"%s",
                  "last_price":%s,
                  "prev_close_price":%s,
                  "volume":%d,
                  "last_trade_time":"%d",
                  "timestamp":"%s",
                  "ohlc":{"open":%s,"high":%s,"low":%s,"close":9999.99,"volume":%d,"ts":1}
                }
                """.formatted(
                "NSE_EQ",
                symbol,
                key,
                symbol,
                lastPrice,
                previousClose,
                volume,
                lastTrade.toEpochMilli(),
                snapshotText,
                open,
                high,
                low,
                candleVolume);
    }

    private static final class MemoryKeys implements UpstoxInstrumentKeySource {
        private final List<UpstoxInstrumentMapping> mappings;

        private MemoryKeys(List<UpstoxInstrumentMapping> mappings) {
            this.mappings = mappings;
        }

        @Override
        public List<UpstoxInstrumentMapping> findForSymbols(Collection<String> symbols) {
            Set<String> wanted = symbols.stream()
                    .map(symbol -> symbol.trim().toUpperCase(Locale.ROOT))
                    .collect(Collectors.toSet());
            return mappings.stream()
                    .filter(mapping -> wanted.contains(mapping.symbol().toUpperCase(Locale.ROOT)))
                    .toList();
        }

        @Override
        public List<UpstoxInstrumentMapping> findAll() {
            return mappings;
        }
    }

    private static final class FakeClient implements UpstoxMarketQuoteClient {
        private final List<List<String>> calls = new ArrayList<>();
        private String body = "{\"status\":\"success\",\"data\":{}}";
        private MarketDataUnavailableException error;
        private Runnable whenResponseReady;

        @Override
        public String fetchQuotes(List<String> instrumentKeys) {
            calls.add(List.copyOf(instrumentKeys));
            if (error != null) {
                throw error;
            }
            if (whenResponseReady != null) {
                whenResponseReady.run();
            }
            return body;
        }
    }
}
