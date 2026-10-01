package com.smartwatch.marketdata.provider;

import com.smartwatch.marketdata.model.HistoricalBar;
import com.smartwatch.marketdata.model.Quote;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MockMarketDataProviderTest {

    private final MockMarketDataProvider provider = new MockMarketDataProvider();

    @Test
    void returnsDeterministicSyntheticQuote() {
        Quote quote = provider.getQuote(" reliance ").orElseThrow();
        Quote again = provider.getQuote("RELIANCE").orElseThrow();

        assertThat(quote).isEqualTo(again);
        assertThat(quote.synthetic()).isTrue();
        assertThat(quote.symbol()).isEqualTo("RELIANCE");
        assertThat(quote.companyName()).isEqualTo("Reliance Industries");
        assertThat(quote.exchange()).isEqualTo(MockMarketDataProvider.LISTING_EXCHANGE);
        assertThat(quote.sector()).isEqualTo("Energy");
        assertThat(quote.price()).isEqualByComparingTo("2500.00");
        assertThat(quote.volume()).isEqualTo(3_200_000L);
        assertThat(quote.timestamp()).isEqualTo(MockMarketDataProvider.SAMPLE_TIMESTAMP);
        assertThat(quote.currency()).isEqualTo(MockMarketDataProvider.CURRENCY);
        assertThat(quote.sessionDate()).isEqualTo(MockMarketDataProvider.SAMPLE_SESSION_DATE);
        assertThat(provider.source()).isEqualTo(MockMarketDataProvider.SOURCE);
        assertThat(MockMarketDataProvider.SYNTHETIC_DATA_NOTICE).contains("SYNTHETIC");
    }

    @Test
    void quoteMatchesTheLatestSyntheticBar() {
        Quote quote = provider.getQuote("INFY").orElseThrow();
        List<HistoricalBar> bars = provider.getHistoricalBars(
                "INFY",
                LocalDate.of(2024, 5, 28),
                MockMarketDataProvider.SAMPLE_SESSION_DATE);

        assertThat(bars).hasSize(5);
        assertThat(bars).allMatch(HistoricalBar::synthetic);
        assertThat(bars.get(4).date()).isEqualTo(MockMarketDataProvider.SAMPLE_SESSION_DATE);
        assertThat(bars.get(4).close()).isEqualByComparingTo(quote.price());
        assertThat(bars.get(4).open()).isEqualByComparingTo(quote.open());
        assertThat(bars.get(4).high()).isEqualByComparingTo(quote.high());
        assertThat(bars.get(4).low()).isEqualByComparingTo(quote.low());
        assertThat(bars.get(4).volume()).isEqualTo(quote.volume());
        assertThat(bars.get(3).close()).isEqualByComparingTo(quote.previousClose());
    }

    @Test
    void quotesPreserveRequestOrderAndSkipUnknownSymbols() {
        assertThat(provider.getQuotes(List.of("ITC", "MISSING", "LT")))
                .extracting(Quote::symbol)
                .containsExactly("ITC", "LT");
    }

    @Test
    void catalogContainsTheSampleIndianInstruments() {
        List<String> symbols = List.of(
                "RELIANCE", "TCS", "INFY", "HDFCBANK", "ICICIBANK",
                "SBIN", "ITC", "BHARTIARTL", "LT", "MARUTI");

        assertThat(provider.getQuotes(symbols))
                .extracting(Quote::symbol)
                .containsExactlyElementsOf(symbols);
    }

    @Test
    void historicalRangeFiltersTheFrozenSessions() {
        List<HistoricalBar> bars = provider.getHistoricalBars(
                "SBIN",
                LocalDate.of(2024, 6, 1),
                LocalDate.of(2024, 6, 3));

        assertThat(bars).extracting(HistoricalBar::date).containsExactly(LocalDate.of(2024, 6, 3));
        assertThat(provider.getHistoricalBars("SBIN", LocalDate.of(2020, 1, 1), LocalDate.of(2020, 1, 5)))
                .isEmpty();
        assertThat(provider.getHistoricalBars("UNKNOWN", LocalDate.of(2024, 5, 1), LocalDate.of(2024, 6, 3)))
                .isEmpty();
    }

    @Test
    void rejectsInvalidRequests() {
        assertThat(provider.getQuote("NOPE")).isEmpty();
        assertThatThrownBy(() -> provider.getQuote(" "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> provider.getQuotes(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> provider.getHistoricalBars("TCS", null, LocalDate.of(2024, 6, 3)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> provider.getHistoricalBars(
                "TCS",
                LocalDate.of(2024, 6, 3),
                LocalDate.of(2024, 6, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void replacementsAreDeterministicAndDoNotChangeTheCatalog() {
        Quote rising = quote("RELIANCE", "2600.00", 9_000_000L);
        Quote falling = quote("TCS", "3600.00", 1_500_000L);
        Quote quiet = quote("INFY", "1800.25", 4_100_000L);

        provider.replaceQuote(rising);
        provider.replaceQuote(falling);
        provider.replaceQuote(quiet);

        assertThat(provider.getQuote("RELIANCE").orElseThrow().price()).isEqualByComparingTo("2600.00");
        assertThat(provider.getQuote("TCS").orElseThrow().price()).isEqualByComparingTo("3600.00");
        assertThat(provider.getQuote("INFY").orElseThrow().volume()).isEqualTo(4_100_000L);
        assertThat(provider.getQuote("RELIANCE")).isEqualTo(provider.getQuote("RELIANCE"));

        provider.clearReplacements();

        assertThat(provider.getQuote("RELIANCE").orElseThrow().price()).isEqualByComparingTo("2500.00");
        assertThat(provider.getQuote("TCS").orElseThrow().price()).isEqualByComparingTo("4000.50");
    }

    private static Quote quote(String symbol, String price, long volume) {
        return new Quote(
                symbol,
                symbol + " Co",
                MockMarketDataProvider.LISTING_EXCHANGE,
                "Test",
                new BigDecimal(price),
                new BigDecimal(price),
                new BigDecimal(price),
                new BigDecimal(price),
                new BigDecimal(price),
                volume,
                MockMarketDataProvider.SAMPLE_TIMESTAMP,
                MockMarketDataProvider.CURRENCY,
                MockMarketDataProvider.SAMPLE_SESSION_DATE,
                true);
    }
}
