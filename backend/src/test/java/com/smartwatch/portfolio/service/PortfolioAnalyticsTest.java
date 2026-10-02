package com.smartwatch.portfolio.service;

import com.smartwatch.marketdata.model.MarketDataQuality;
import com.smartwatch.marketdata.model.MarketDataSource;
import com.smartwatch.portfolio.dto.HoldingAnalyticsResponse;
import com.smartwatch.portfolio.dto.InstrumentAllocationResponse;
import com.smartwatch.portfolio.dto.PortfolioAnalyticsResponse;
import com.smartwatch.portfolio.dto.SectorAllocationResponse;
import com.smartwatch.portfolio.service.PortfolioAnalytics.PositionInput;
import com.smartwatch.portfolio.service.PortfolioAnalytics.QuoteSnapshot;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class PortfolioAnalyticsTest {

    private static final UUID PORTFOLIO_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Instant MARKET_TIME = Instant.parse("2024-06-03T10:00:00Z");
    private static final Instant OBSERVED_TIME = Instant.parse("2024-06-03T10:00:05Z");

    @Test
    void calculatesProfitAndLossForAWinnerAndALoser() {
        PortfolioAnalyticsResponse result = PortfolioAnalytics.calculate(PORTFOLIO_ID, List.of(
                position("RELIANCE", "Energy", "10", "2000", quote("2500", "INR", MarketDataQuality.REAL_TIME)),
                position("TCS", "Technology", "5", "4000", quote("3000", "INR", MarketDataQuality.REAL_TIME))));

        assertThat(result.totalInvested()).isEqualTo(new BigDecimal("40000.0000"));
        assertThat(result.currentValue()).isEqualTo(new BigDecimal("40000.0000"));
        assertThat(result.totalPnl()).isEqualTo(new BigDecimal("0.0000"));
        assertThat(result.returnPercent()).isEqualTo(new BigDecimal("0.00"));
        assertThat(result.winners()).isEqualTo(1);
        assertThat(result.losers()).isEqualTo(1);
        assertThat(result.currency()).isEqualTo("INR");
        assertThat(result.mixedCurrencies()).isFalse();
        assertThat(result.realTime()).isTrue();
        assertThat(result.best().symbol()).isEqualTo("RELIANCE");
        assertThat(result.best().pnl()).isEqualTo(new BigDecimal("5000.0000"));
        assertThat(result.best().returnPercent()).isEqualTo(new BigDecimal("25.00"));
        assertThat(result.worst().symbol()).isEqualTo("TCS");
        assertThat(result.worst().pnl()).isEqualTo(new BigDecimal("-5000.0000"));
        assertThat(result.worst().returnPercent()).isEqualTo(new BigDecimal("-25.00"));
    }

    @Test
    void keepsAMissingQuoteInInvestedAndAllocationOnly() {
        PortfolioAnalyticsResponse result = PortfolioAnalytics.calculate(PORTFOLIO_ID, List.of(
                position("RELIANCE", "Energy", "10", "2000", quote("2500", "INR", MarketDataQuality.REAL_TIME)),
                position("INFY", "Technology", "2", "1000", null)));

        assertThat(result.totalInvested()).isEqualTo(new BigDecimal("22000.0000"));
        assertThat(result.currentValue()).isEqualTo(new BigDecimal("25000.0000"));
        assertThat(result.totalPnl()).isEqualTo(new BigDecimal("5000.0000"));
        assertThat(result.returnPercent()).isEqualTo(new BigDecimal("25.00"));
        assertThat(result.unvaluedPositions()).isEqualTo(1);
        assertThat(result.winners()).isEqualTo(1);
        assertThat(result.losers()).isEqualTo(0);
        assertThat(result.best().symbol()).isEqualTo("RELIANCE");
        assertThat(result.worst().symbol()).isEqualTo("RELIANCE");
        assertThat(result.holdings()).filteredOn(holding -> holding.symbol().equals("INFY"))
                .singleElement()
                .satisfies(holding -> {
                    assertThat(holding.invested()).isEqualTo(new BigDecimal("2000.0000"));
                    assertThat(holding.currentValue()).isNull();
                    assertThat(holding.pnl()).isNull();
                    assertThat(holding.returnPercent()).isNull();
                    assertThat(holding.quality()).isNull();
                });
        assertThat(result.sectorAllocations()).extracting(SectorAllocationResponse::sector)
                .containsExactly("Energy", "Technology");
        assertThat(percentage(result, "Energy")).isEqualTo(new BigDecimal("90.91"));
        assertThat(percentage(result, "Technology")).isEqualTo(new BigDecimal("9.09"));
        assertThat(result.instrumentAllocations()).extracting(InstrumentAllocationResponse::symbol)
                .containsExactly("INFY", "RELIANCE");
    }

    @Test
    void countsAStaleQuoteWhenItStillHasAPrice() {
        PortfolioAnalyticsResponse result = PortfolioAnalytics.calculate(PORTFOLIO_ID, List.of(
                position("RELIANCE", "Energy", "1", "8", quote("10", "INR", MarketDataQuality.STALE))));

        assertThat(result.currentValue()).isEqualTo(new BigDecimal("10.0000"));
        assertThat(result.totalPnl()).isEqualTo(new BigDecimal("2.0000"));
        assertThat(result.returnPercent()).isEqualTo(new BigDecimal("25.00"));
        assertThat(result.realTime()).isFalse();
        assertThat(result.best().quality()).isEqualTo(MarketDataQuality.STALE);
        assertThat(result.best().source()).isEqualTo(MarketDataSource.MOCK);
        assertThat(result.best().marketTimestamp()).isEqualTo(MARKET_TIME);
        assertThat(result.best().observedAt()).isEqualTo(OBSERVED_TIME);
    }

    @Test
    void countsAnUnknownQuoteWhenItStillHasAPrice() {
        PortfolioAnalyticsResponse result = PortfolioAnalytics.calculate(PORTFOLIO_ID, List.of(
                position("TCS", "Technology", "1", "10", quote("5", "INR", MarketDataQuality.UNKNOWN))));

        assertThat(result.currentValue()).isEqualTo(new BigDecimal("5.0000"));
        assertThat(result.totalPnl()).isEqualTo(new BigDecimal("-5.0000"));
        assertThat(result.losers()).isEqualTo(1);
        assertThat(result.realTime()).isFalse();
        assertThat(result.best().quality()).isEqualTo(MarketDataQuality.UNKNOWN);
    }

    @Test
    void treatsAFlatResultAsNeitherWinnerNorLoser() {
        PortfolioAnalyticsResponse result = PortfolioAnalytics.calculate(PORTFOLIO_ID, List.of(
                position("INFY", "Technology", "2", "10", quote("10", "INR", MarketDataQuality.REAL_TIME))));

        assertThat(result.totalPnl()).isEqualTo(new BigDecimal("0.0000"));
        assertThat(result.returnPercent()).isEqualTo(new BigDecimal("0.00"));
        assertThat(result.winners()).isZero();
        assertThat(result.losers()).isZero();
        assertThat(result.best().symbol()).isEqualTo("INFY");
        assertThat(result.worst().symbol()).isEqualTo("INFY");
    }

    @Test
    void breaksEqualReturnsBySymbolAscending() {
        PortfolioAnalyticsResponse result = PortfolioAnalytics.calculate(PORTFOLIO_ID, List.of(
                position("DEF", "Energy", "1", "100", quote("110", "INR", MarketDataQuality.REAL_TIME)),
                position("ZEBRA", "Energy", "1", "100", quote("99", "INR", MarketDataQuality.REAL_TIME)),
                position("ABC", "Energy", "1", "100", quote("110", "INR", MarketDataQuality.REAL_TIME))));

        assertThat(result.best().symbol()).isEqualTo("ABC");
        assertThat(result.best().returnPercent()).isEqualTo(new BigDecimal("10.00"));
        assertThat(result.worst().symbol()).isEqualTo("ZEBRA");
        assertThat(result.worst().returnPercent()).isEqualTo(new BigDecimal("-1.00"));
    }

    @Test
    void returnsZerosForAnEmptyPortfolio() {
        PortfolioAnalyticsResponse result = PortfolioAnalytics.calculate(PORTFOLIO_ID, List.of());

        assertThat(result.totalInvested()).isEqualTo(new BigDecimal("0.0000"));
        assertThat(result.currentValue()).isEqualTo(new BigDecimal("0.0000"));
        assertThat(result.totalPnl()).isEqualTo(new BigDecimal("0.0000"));
        assertThat(result.returnPercent()).isNull();
        assertThat(result.winners()).isZero();
        assertThat(result.losers()).isZero();
        assertThat(result.unvaluedPositions()).isZero();
        assertThat(result.best()).isNull();
        assertThat(result.worst()).isNull();
        assertThat(result.sectorAllocations()).isEmpty();
        assertThat(result.instrumentAllocations()).isEmpty();
        assertThat(result.holdings()).isEmpty();
        assertThat(result.currency()).isNull();
        assertThat(result.mixedCurrencies()).isFalse();
        assertThat(result.realTime()).isFalse();
    }

    @Test
    void groupsSectorAndInstrumentAllocationFromInvestedAmount() {
        UUID reliance = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1");
        UUID tcs = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2");
        UUID infy = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa3");
        PortfolioAnalyticsResponse result = PortfolioAnalytics.calculate(PORTFOLIO_ID, List.of(
                position(reliance, "RELIANCE", "Energy", "1", "1", quote("2", "INR", MarketDataQuality.REAL_TIME)),
                position(tcs, "TCS", "Technology", "1", "1", quote("2", "INR", MarketDataQuality.REAL_TIME)),
                position(infy, "INFY", "Technology", "1", "1", null)));

        assertThat(result.sectorAllocations()).extracting(SectorAllocationResponse::sector)
                .containsExactly("Energy", "Technology");
        assertThat(percentage(result, "Energy")).isEqualTo(new BigDecimal("33.33"));
        assertThat(percentage(result, "Technology")).isEqualTo(new BigDecimal("66.67"));
        assertThat(share(result.sectorAllocations())).isCloseTo(new BigDecimal("100.00"), within(new BigDecimal("0.02")));

        assertThat(result.instrumentAllocations()).extracting(InstrumentAllocationResponse::instrumentId)
                .containsExactly(infy, reliance, tcs);
        assertThat(result.instrumentAllocations()).allSatisfy(slice ->
                assertThat(slice.percentage()).isEqualTo(new BigDecimal("33.33")));
        assertThat(result.instrumentAllocations().stream()
                .map(InstrumentAllocationResponse::percentage)
                .reduce(BigDecimal.ZERO, BigDecimal::add))
                .isCloseTo(new BigDecimal("100.00"), within(new BigDecimal("0.02")));
    }

    @Test
    void doesNotCombineMixedCurrencies() {
        PortfolioAnalyticsResponse result = PortfolioAnalytics.calculate(PORTFOLIO_ID, List.of(
                position("RELIANCE", "Energy", "1", "100", quote("110", "INR", MarketDataQuality.REAL_TIME)),
                position("AAPL", "Technology", "1", "100", quote("90", "USD", MarketDataQuality.DELAYED))));

        assertThat(result.mixedCurrencies()).isTrue();
        assertThat(result.currency()).isNull();
        assertThat(result.totalInvested()).isNull();
        assertThat(result.currentValue()).isNull();
        assertThat(result.totalPnl()).isNull();
        assertThat(result.returnPercent()).isNull();
        assertThat(result.sectorAllocations()).isEmpty();
        assertThat(result.instrumentAllocations()).isEmpty();
        assertThat(result.realTime()).isFalse();
        assertThat(result.winners()).isEqualTo(1);
        assertThat(result.losers()).isEqualTo(1);
        assertThat(result.holdings()).extracting(HoldingAnalyticsResponse::currency).containsExactly("USD", "INR");
        assertThat(result.holdings()).allSatisfy(holding -> {
            assertThat(holding.invested()).isEqualTo(new BigDecimal("100.0000"));
            assertThat(holding.pnl()).isNotNull();
            assertThat(holding.returnPercent()).isNotNull();
        });
    }

    private static BigDecimal percentage(PortfolioAnalyticsResponse result, String sector) {
        return result.sectorAllocations().stream()
                .filter(slice -> slice.sector().equals(sector))
                .map(SectorAllocationResponse::percentage)
                .findFirst()
                .orElseThrow();
    }

    private static BigDecimal share(List<SectorAllocationResponse> slices) {
        return slices.stream().map(SectorAllocationResponse::percentage).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static PositionInput position(
            String symbol,
            String sector,
            String quantity,
            String averageBuyPrice,
            QuoteSnapshot quote) {
        return position(UUID.randomUUID(), symbol, sector, quantity, averageBuyPrice, quote);
    }

    private static PositionInput position(
            UUID instrumentId,
            String symbol,
            String sector,
            String quantity,
            String averageBuyPrice,
            QuoteSnapshot quote) {
        return new PositionInput(
                instrumentId,
                symbol,
                "NSE",
                sector,
                new BigDecimal(quantity),
                new BigDecimal(averageBuyPrice),
                quote);
    }

    private static QuoteSnapshot quote(String price, String currency, MarketDataQuality quality) {
        return new QuoteSnapshot(
                new BigDecimal(price),
                currency,
                quality,
                MarketDataSource.MOCK,
                MARKET_TIME,
                OBSERVED_TIME);
    }
}
