package com.smartwatch.assistant;

import com.smartwatch.assistant.dto.AssistantAnswerResponse;
import com.smartwatch.change.ChangeSeverity;
import com.smartwatch.change.ChangeType;
import com.smartwatch.marketdata.model.MarketDataQuality;
import com.smartwatch.marketdata.model.MarketDataSource;
import com.smartwatch.portfolio.dto.HoldingAnalyticsResponse;
import com.smartwatch.portfolio.dto.PortfolioAnalyticsResponse;
import com.smartwatch.portfolio.dto.SectorAllocationResponse;
import com.smartwatch.watchlist.dto.ChangeResponse;
import com.smartwatch.watchlist.dto.ChangeSummaryResponse;
import com.smartwatch.watchlist.dto.WatchlistChangesResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AssistantReplyBuilderTest {

    private static final UUID PORTFOLIO_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1");
    private static final Instant WHEN = Instant.parse("2024-06-03T10:00:00Z");

    @Test
    void reportsTheBestPerformerFromAnalytics() {
        AssistantAnswerResponse answer = ask("Which investment has performed best?", book());

        assertThat(answer.refused()).isFalse();
        assertThat(answer.sources()).containsExactly(AssistantSources.PORTFOLIO_ANALYTICS);
        assertThat(answer.answer()).contains("RELIANCE", "Long Term", "25.00%");
        assertThat(answer.answer()).doesNotContain("TCS has the best");
    }

    @Test
    void reportsTheWorstPerformerFromAnalytics() {
        AssistantAnswerResponse answer = ask("Which investment has performed worst?", book());

        assertThat(answer.answer()).contains("TCS", "Long Term", "-25.00%");
        assertThat(answer.sources()).containsExactly(AssistantSources.PORTFOLIO_ANALYTICS);
    }

    @Test
    void reportsTotalPnlFromAnalytics() {
        AssistantAnswerResponse answer = ask("What is my total P&L?", book());

        assertThat(answer.answer()).contains("Long Term", "0.0000 INR");
        assertThat(answer.sources()).containsExactly(AssistantSources.PORTFOLIO_ANALYTICS);
    }

    @Test
    void reportsReturnFromAnalytics() {
        AssistantAnswerResponse answer = ask("What is my return?", book());

        assertThat(answer.answer()).contains("Long Term", "0.00%");
        assertThat(answer.refused()).isFalse();
    }

    @Test
    void reportsSectorDistributionFromAnalytics() {
        AssistantAnswerResponse answer = ask("How is my portfolio distributed across sectors?", book());

        assertThat(answer.answer()).contains("Energy (50.00%)", "Technology (50.00%)");
        assertThat(answer.sources()).containsExactly(AssistantSources.PORTFOLIO_ANALYTICS);
    }

    @Test
    void reportsUnvaluedHoldingsWithoutInventingAPrice() {
        AssistantContext context = context(portfolio("Long Term", analytics(
                new BigDecimal("22000.0000"),
                new BigDecimal("25000.0000"),
                new BigDecimal("5000.0000"),
                new BigDecimal("25.00"),
                false,
                false,
                1,
                0,
                1,
                holding("RELIANCE", "25000.0000", "5000.0000", "25.00", MarketDataQuality.REAL_TIME),
                (HoldingAnalyticsResponse) null,
                List.of(new SectorAllocationResponse("Energy", new BigDecimal("20000.0000"), new BigDecimal("90.91"))),
                List.of(
                        holding("INFY", null, null, null, null),
                        holding("RELIANCE", "25000.0000", "5000.0000", "25.00", MarketDataQuality.REAL_TIME)))));

        AssistantAnswerResponse answer = ask("Which holdings are unvalued?", context);

        assertThat(answer.answer()).contains("INFY", "cannot be determined");
        assertThat(answer.answer()).doesNotContain("INFY in Long Term has no quote, so its current value is");
        assertThat(answer.answer()).doesNotContain("999");
    }

    @Test
    void reportsWatchlistChangesWithoutAcknowledgingACheck() {
        WatchlistChangesResponse changes = new WatchlistChangesResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                WHEN,
                false,
                List.of(new ChangeResponse(
                        UUID.randomUUID(),
                        "RELIANCE",
                        "NSE",
                        ChangeType.PRICE_MOVE,
                        ChangeSeverity.NOTABLE,
                        new BigDecimal("2500.0000"),
                        new BigDecimal("2400.0000"),
                        new BigDecimal("100.0000"),
                        new BigDecimal("4.17"),
                        "INR",
                        WHEN,
                        "Price rose against the last check.")),
                new ChangeSummaryResponse(1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, List.of("RELIANCE moved.")));
        AssistantContext context = new AssistantContext(
                List.of(),
                List.of(new AssistantContext.WatchlistSnapshot(UUID.randomUUID(), "Core", changes)));

        AssistantAnswerResponse answer = ask("What changed since I last checked?", context);

        assertThat(answer.sources()).containsExactly(AssistantSources.WATCHLIST_CHANGES);
        assertThat(answer.answer()).contains("Core", "RELIANCE", "Price movement", "Price rose against the last check.");
    }

    @Test
    void summarizesAPortfolioFromExistingFigures() {
        AssistantAnswerResponse answer = ask("Summarize my portfolio.", book());

        assertThat(answer.sources()).containsExactly(AssistantSources.PORTFOLIO_SUMMARY);
        assertThat(answer.answer()).contains(
                "Long Term",
                "40000.0000 INR",
                "0.0000 INR",
                "RELIANCE",
                "TCS",
                "1 winner",
                "1 loser",
                "Energy");
    }

    @Test
    void explainsUnsupportedQuestions() {
        AssistantAnswerResponse answer = ask("What is the capital of France?", book());

        assertThat(answer.refused()).isFalse();
        assertThat(answer.sources()).isEmpty();
        assertThat(answer.answer()).contains("stored portfolio and watchlist data");
    }

    @Test
    void refusesABuyRecommendation() {
        AssistantAnswerResponse answer = ask("Should I buy RELIANCE?", book());

        assertThat(answer.refused()).isTrue();
        assertThat(answer.answer()).isEqualTo(AssistantReplyBuilder.REFUSAL);
        assertThat(answer.sources()).isEmpty();
    }

    @Test
    void refusesASellRecommendation() {
        AssistantAnswerResponse answer = ask("Should I sell TCS?", book());

        assertThat(answer.refused()).isTrue();
        assertThat(answer.answer()).isEqualTo(AssistantReplyBuilder.REFUSAL);
    }

    @Test
    void refusesAPricePrediction() {
        AssistantAnswerResponse answer = ask("Will this stock go up?", book());

        assertThat(answer.refused()).isTrue();
        assertThat(answer.answer()).isEqualTo(AssistantReplyBuilder.REFUSAL);
    }

    @Test
    void doesNotCallStaleOrUnknownQuotesRealTime() {
        AssistantContext stale = context(portfolio("Long Term", analytics(
                money("100"), money("90"), money("-10"), new BigDecimal("-10.00"),
                false, false, 0, 1, 0,
                holding("TCS", "90.0000", "-10.0000", "-10.00", MarketDataQuality.STALE),
                holding("TCS", "90.0000", "-10.0000", "-10.00", MarketDataQuality.STALE),
                List.of(),
                List.of(holding("TCS", "90.0000", "-10.0000", "-10.00", MarketDataQuality.STALE)))));
        AssistantContext unknown = context(portfolio("Long Term", analytics(
                money("100"), money("110"), money("10"), new BigDecimal("10.00"),
                false, false, 1, 0, 0,
                holding("INFY", "110.0000", "10.0000", "10.00", MarketDataQuality.UNKNOWN),
                holding("INFY", "110.0000", "10.0000", "10.00", MarketDataQuality.UNKNOWN),
                List.of(),
                List.of(holding("INFY", "110.0000", "10.0000", "10.00", MarketDataQuality.UNKNOWN)))));

        assertThat(ask("Which investment has performed best?", stale).answer())
                .contains("stale, not real-time")
                .doesNotContain("The quote is real-time.");
        assertThat(ask("What is my best performing investment?", unknown).answer())
                .contains("unknown, not real-time")
                .doesNotContain("The quote is real-time.");
    }

    @Test
    void doesNotCombineMixedCurrencies() {
        AssistantContext context = new AssistantContext(List.of(
                portfolio("India", analytics(
                        money("20000"), money("25000"), money("5000"), new BigDecimal("25.00"),
                        false, false, 1, 0, 0,
                        holding("RELIANCE", "25000.0000", "5000.0000", "25.00", MarketDataQuality.END_OF_DAY),
                        holding("RELIANCE", "25000.0000", "5000.0000", "25.00", MarketDataQuality.END_OF_DAY),
                        List.of(new SectorAllocationResponse("Energy", money("20000"), new BigDecimal("100.00"))),
                        List.of(holding("RELIANCE", "25000.0000", "5000.0000", "25.00", MarketDataQuality.END_OF_DAY)))),
                portfolio("Foreign", analytics(
                        money("100"), money("110"), money("10"), new BigDecimal("10.00"),
                        false, false, 1, 0, 0,
                        holding("AAPL", "110.0000", "10.0000", "10.00", MarketDataQuality.DELAYED, "USD"),
                        holding("AAPL", "110.0000", "10.0000", "10.00", MarketDataQuality.DELAYED, "USD"),
                        List.of(),
                        List.of(holding("AAPL", "110.0000", "10.0000", "10.00", MarketDataQuality.DELAYED, "USD"))))), List.of());

        AssistantAnswerResponse answer = ask("What is my total P&L?", context);

        assertThat(answer.answer()).contains("not combined", "5000.0000 INR", "10.0000 USD");
        assertThat(answer.answer()).doesNotContain("5010");
    }

    @Test
    void doesNotFabricateAPriceForAMissingQuote() {
        AssistantContext context = context(portfolio("Long Term", analytics(
                money("2000"),
                new BigDecimal("0.0000"),
                new BigDecimal("0.0000"),
                null,
                false,
                false,
                0,
                0,
                1,
                (HoldingAnalyticsResponse) null,
                (HoldingAnalyticsResponse) null,
                List.of(new SectorAllocationResponse("Technology", money("2000"), new BigDecimal("100.00"))),
                List.of(holding("INFY", null, null, null, null)))));

        AssistantAnswerResponse answer = ask("Why isn't everything valued?", context);

        assertThat(answer.answer()).contains("INFY", "cannot be determined");
        assertThat(answer.answer()).doesNotContain("INFY in Long Term is worth");
    }

    private static AssistantAnswerResponse ask(String question, AssistantContext context) {
        return AssistantReplyBuilder.reply(question, context);
    }

    private static AssistantContext book() {
        HoldingAnalyticsResponse reliance = holding("RELIANCE", "25000.0000", "5000.0000", "25.00", MarketDataQuality.END_OF_DAY);
        HoldingAnalyticsResponse tcs = holding("TCS", "15000.0000", "-5000.0000", "-25.00", MarketDataQuality.END_OF_DAY);
        return context(portfolio("Long Term", analytics(
                money("40000"),
                money("40000"),
                money("0"),
                new BigDecimal("0.00"),
                false,
                false,
                1,
                1,
                0,
                reliance,
                tcs,
                List.of(
                        new SectorAllocationResponse("Energy", money("20000"), new BigDecimal("50.00")),
                        new SectorAllocationResponse("Technology", money("20000"), new BigDecimal("50.00"))),
                List.of(reliance, tcs))));
    }

    private static AssistantContext context(AssistantContext.PortfolioSnapshot portfolio) {
        return new AssistantContext(List.of(portfolio), List.of());
    }

    private static AssistantContext.PortfolioSnapshot portfolio(String name, PortfolioAnalyticsResponse analytics) {
        return new AssistantContext.PortfolioSnapshot(PORTFOLIO_ID, name, analytics);
    }

    private static PortfolioAnalyticsResponse analytics(
            BigDecimal invested,
            BigDecimal current,
            BigDecimal pnl,
            BigDecimal ret,
            boolean realTime,
            boolean mixed,
            int winners,
            int losers,
            int unvalued,
            HoldingAnalyticsResponse best,
            HoldingAnalyticsResponse worst,
            List<com.smartwatch.portfolio.dto.SectorAllocationResponse> sectors,
            List<HoldingAnalyticsResponse> holdings) {
        String currency = mixed ? null : holdings.stream()
                .map(HoldingAnalyticsResponse::currency)
                .filter(value -> value != null)
                .findFirst()
                .orElse("INR");
        return new PortfolioAnalyticsResponse(
                PORTFOLIO_ID,
                currency,
                mixed,
                realTime,
                invested,
                current,
                pnl,
                ret,
                winners,
                losers,
                unvalued,
                best,
                worst,
                sectors,
                List.of(),
                holdings);
    }

    private static HoldingAnalyticsResponse holding(
            String symbol,
            String value,
            String pnl,
            String ret,
            MarketDataQuality quality) {
        return holding(symbol, value, pnl, ret, quality, "INR");
    }

    private static HoldingAnalyticsResponse holding(
            String symbol,
            String value,
            String pnl,
            String ret,
            MarketDataQuality quality,
            String currency) {
        return new HoldingAnalyticsResponse(
                UUID.randomUUID(),
                symbol,
                "NSE",
                "Energy",
                value == null ? null : currency,
                new BigDecimal("100.0000"),
                value == null ? null : new BigDecimal(value),
                pnl == null ? null : new BigDecimal(pnl),
                ret == null ? null : new BigDecimal(ret),
                quality,
                value == null ? null : MarketDataSource.MOCK,
                value == null ? null : WHEN,
                value == null ? null : WHEN);
    }

    private static BigDecimal money(String amount) {
        return new BigDecimal(amount).setScale(4);
    }
}
