package com.smartwatch.assistant;

import com.smartwatch.assistant.dto.AssistantAnswerResponse;
import com.smartwatch.change.ChangeType;
import com.smartwatch.marketdata.model.MarketDataQuality;
import com.smartwatch.portfolio.dto.HoldingAnalyticsResponse;
import com.smartwatch.portfolio.dto.PortfolioAnalyticsResponse;
import com.smartwatch.portfolio.dto.SectorAllocationResponse;
import com.smartwatch.watchlist.dto.ChangeResponse;
import com.smartwatch.watchlist.dto.WatchlistChangesResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;

/**
 * Deterministic answers from analytics and change responses already calculated
 * elsewhere. This class does not recompute a return and does not open a network
 * connection.
 */
public final class AssistantReplyBuilder {

    static final String REFUSAL = "I can report your stored portfolio and watchlist data, but I don't predict prices or recommend buying or selling securities.";

    private static final String UNSUPPORTED = "V1 can answer questions about your stored portfolio and watchlist data, such as performance, profit and loss, current value, how many holdings you have, which holdings are losing money, return, sectors, unvalued holdings, and changes since your last check.";
    private static final int MESSAGE_LIMIT = 240;

    private AssistantReplyBuilder() {
    }

    public static AssistantAnswerResponse reply(String question, AssistantContext context) {
        Intent intent = classify(question);
        return switch (intent) {
            case REFUSAL -> refused();
            case BEST -> performers(context, true);
            case WORST -> performers(context, false);
            case BOTH -> bothPerformers(context);
            case PNL -> pnl(context);
            case VALUE -> currentValue(context);
            case COUNT -> holdingCount(context);
            case LOSING -> losing(context);
            case RETURN -> returns(context);
            case SECTOR -> sectors(context);
            case UNVALUED -> unvalued(context);
            case WATCHLIST -> watchlists(context);
            case SUMMARY -> summary(context);
            case UNSUPPORTED -> new AssistantAnswerResponse(UNSUPPORTED, false, List.of());
        };
    }

    private static Intent classify(String question) {
        String lower = question.toLowerCase(Locale.ROOT);
        if (refused(lower)) {
            return Intent.REFUSAL;
        }
        String text = lower.replaceAll("[^a-z0-9&\\s]", " ").replaceAll("\\s+", " ").trim();
        boolean best = containsAny(text, "performed best", "best performing", "doing best", "best investment", "best holding", "best performer");
        boolean worst = containsAny(text, "performed worst", "worst performing", "doing worst", "worst investment", "worst holding", "worst performer");
        if (best && worst) {
            return Intent.BOTH;
        }
        if (containsAny(text, "unvalued", "missing price", "everything valued", "no quote", "without a price")) {
            return Intent.UNVALUED;
        }
        if (text.contains("sector")) {
            return Intent.SECTOR;
        }
        if (text.contains("watchlist") || text.contains("last check") || (text.contains("what changed") && !text.contains("portfolio"))) {
            return Intent.WATCHLIST;
        }
        if (containsAny(text, "how many holdings", "how many positions", "number of holdings", "number of positions")) {
            return Intent.COUNT;
        }
        if (containsAny(text, "losing money", "currently losing", "are losers", "losing holdings")) {
            return Intent.LOSING;
        }
        if (worst) {
            return Intent.WORST;
        }
        if (best) {
            return Intent.BEST;
        }
        if (containsAny(text, "current value", "portfolio value", "portfolio worth")) {
            return Intent.VALUE;
        }
        if (containsAny(text, "p&l", "pnl", "profit and loss", "profit", "how much have i made", "up or down")) {
            return Intent.PNL;
        }
        if (text.contains("return")) {
            return Intent.RETURN;
        }
        if (containsAny(text, "summarize", "summary", "overview", "how is my portfolio", "portfolio doing")) {
            return Intent.SUMMARY;
        }
        return Intent.UNSUPPORTED;
    }

    private static boolean refused(String lower) {
        if (containsAny(lower,
                "should i buy", "should i sell", "should we buy", "should we sell",
                "buy or sell", "recommend buying", "recommend selling",
                "price target", "future return", "future price", "guaranteed", "guarantee")) {
            return true;
        }
        if (lower.contains("predict") || lower.contains("forecast")) {
            return true;
        }
        boolean lookingAhead = lower.contains("will ") || lower.contains("going to");
        boolean movement = containsAny(lower, "go up", "go down", "rise", "fall");
        return lookingAhead && (movement || lower.contains("stock") || lower.contains("price"));
    }

    private static AssistantAnswerResponse refused() {
        return new AssistantAnswerResponse(REFUSAL, true, List.of());
    }

    private static AssistantAnswerResponse performers(AssistantContext context, boolean best) {
        if (context.portfolios().isEmpty()) {
            return analytics(noPortfolios());
        }
        StringBuilder answer = new StringBuilder();
        for (AssistantContext.PortfolioSnapshot portfolio : context.portfolios()) {
            HoldingAnalyticsResponse holding = best ? portfolio.analytics().best() : portfolio.analytics().worst();
            if (holding == null) {
                answer.append("No holding in ")
                        .append(portfolio.name())
                        .append(" has a quote, so a ")
                        .append(best ? "best" : "worst")
                        .append(" performer cannot be chosen. ");
            } else {
                answer.append(performerSentence(portfolio.name(), holding, best)).append(' ');
            }
        }
        return analytics(answer.toString().trim());
    }

    private static AssistantAnswerResponse bothPerformers(AssistantContext context) {
        if (context.portfolios().isEmpty()) {
            return analytics(noPortfolios());
        }
        StringBuilder answer = new StringBuilder();
        for (AssistantContext.PortfolioSnapshot portfolio : context.portfolios()) {
            HoldingAnalyticsResponse best = portfolio.analytics().best();
            HoldingAnalyticsResponse worst = portfolio.analytics().worst();
            if (best == null || worst == null) {
                answer.append("No holding in ")
                        .append(portfolio.name())
                        .append(" has a quote, so a best or worst performer cannot be chosen. ");
            } else {
                answer.append(performerSentence(portfolio.name(), best, true)).append(' ');
                answer.append(performerSentence(portfolio.name(), worst, false)).append(' ');
            }
        }
        return analytics(answer.toString().trim());
    }

    private static AssistantAnswerResponse currentValue(AssistantContext context) {
        if (context.portfolios().isEmpty()) {
            return analytics(noPortfolios());
        }
        if (incompatible(context.portfolios())) {
            return analytics(uncombinedValues(context.portfolios()));
        }
        StringBuilder answer = new StringBuilder();
        if (context.portfolios().size() > 1) {
            answer.append("Current value is reported separately for each portfolio. ");
        }
        for (AssistantContext.PortfolioSnapshot portfolio : context.portfolios()) {
            answer.append(valueSentence(portfolio)).append(' ');
        }
        return analytics(answer.toString().trim());
    }

    private static AssistantAnswerResponse holdingCount(AssistantContext context) {
        if (context.portfolios().isEmpty()) {
            return analytics(noPortfolios());
        }
        StringBuilder answer = new StringBuilder();
        if (context.portfolios().size() > 1) {
            int total = context.portfolios().stream()
                    .mapToInt(portfolio -> portfolio.analytics().holdings().size())
                    .sum();
            answer.append("You have ")
                    .append(total)
                    .append(total == 1 ? " holding" : " holdings")
                    .append(" across your portfolios. ");
        }
        for (AssistantContext.PortfolioSnapshot portfolio : context.portfolios()) {
            int count = portfolio.analytics().holdings().size();
            answer.append(portfolio.name())
                    .append(" has ")
                    .append(count)
                    .append(count == 1 ? " holding. " : " holdings. ");
        }
        return analytics(answer.toString().trim());
    }

    private static AssistantAnswerResponse losing(AssistantContext context) {
        if (context.portfolios().isEmpty()) {
            return analytics(noPortfolios());
        }
        StringBuilder answer = new StringBuilder();
        for (AssistantContext.PortfolioSnapshot portfolio : context.portfolios()) {
            answer.append(losingSentence(portfolio)).append(' ');
        }
        return analytics(answer.toString().trim());
    }

    private static AssistantAnswerResponse pnl(AssistantContext context) {
        if (context.portfolios().isEmpty()) {
            return analytics(noPortfolios());
        }
        if (incompatible(context.portfolios())) {
            return analytics(uncombined(context.portfolios(), "P&L"));
        }
        StringBuilder answer = new StringBuilder();
        if (context.portfolios().size() > 1) {
            answer.append("Across ")
                    .append(context.portfolios().get(0).analytics().currency())
                    .append(" portfolios, the reported P&L figures add up to ")
                    .append(money(sumPnl(context.portfolios()), context.portfolios().get(0).analytics().currency()))
                    .append(". ");
        }
        for (AssistantContext.PortfolioSnapshot portfolio : context.portfolios()) {
            answer.append(pnlSentence(portfolio)).append(' ');
        }
        return analytics(answer.toString().trim());
    }

    private static AssistantAnswerResponse returns(AssistantContext context) {
        if (context.portfolios().isEmpty()) {
            return analytics(noPortfolios());
        }
        if (incompatible(context.portfolios())) {
            return analytics("These portfolios are not combined into one return. " + perBookReturns(context.portfolios()));
        }
        StringBuilder answer = new StringBuilder();
        if (context.portfolios().size() > 1) {
            answer.append("Return is reported separately for each portfolio. ");
        }
        for (AssistantContext.PortfolioSnapshot portfolio : context.portfolios()) {
            answer.append(returnSentence(portfolio)).append(' ');
        }
        return analytics(answer.toString().trim());
    }

    private static AssistantAnswerResponse sectors(AssistantContext context) {
        if (context.portfolios().isEmpty()) {
            return analytics(noPortfolios());
        }
        StringBuilder answer = new StringBuilder();
        for (AssistantContext.PortfolioSnapshot portfolio : context.portfolios()) {
            answer.append(sectorSentence(portfolio)).append(' ');
        }
        return analytics(answer.toString().trim());
    }

    private static AssistantAnswerResponse unvalued(AssistantContext context) {
        if (context.portfolios().isEmpty()) {
            return analytics(noPortfolios());
        }
        int count = context.portfolios().stream().mapToInt(portfolio -> portfolio.analytics().unvaluedPositions()).sum();
        if (count == 0) {
            return analytics("No holdings are missing a quote.");
        }
        StringBuilder answer = new StringBuilder();
        answer.append(count).append(count == 1 ? " holding is" : " holdings are").append(" missing a quote. ");
        for (AssistantContext.PortfolioSnapshot portfolio : context.portfolios()) {
            for (HoldingAnalyticsResponse holding : portfolio.analytics().holdings()) {
                if (holding.currentValue() == null) {
                    answer.append(unvaluedSentence(portfolio.name(), holding)).append(' ');
                }
            }
        }
        return analytics(answer.toString().trim());
    }

    private static AssistantAnswerResponse watchlists(AssistantContext context) {
        if (context.watchlists().isEmpty()) {
            return new AssistantAnswerResponse("You do not have a watchlist yet.", false, List.of(AssistantSources.WATCHLIST_CHANGES));
        }
        StringBuilder answer = new StringBuilder();
        for (AssistantContext.WatchlistSnapshot watchlist : context.watchlists()) {
            answer.append(watchlistSentence(watchlist)).append(' ');
        }
        return new AssistantAnswerResponse(answer.toString().trim(), false, List.of(AssistantSources.WATCHLIST_CHANGES));
    }

    private static AssistantAnswerResponse summary(AssistantContext context) {
        if (context.portfolios().isEmpty()) {
            return new AssistantAnswerResponse(noPortfolios(), false, List.of(AssistantSources.PORTFOLIO_SUMMARY));
        }
        StringBuilder answer = new StringBuilder();
        if (context.portfolios().size() > 1 && incompatible(context.portfolios())) {
            answer.append("These portfolios use different currencies, so they are not combined into one total. ");
        }
        for (AssistantContext.PortfolioSnapshot portfolio : context.portfolios()) {
            answer.append(summarySentence(portfolio)).append(' ');
        }
        return new AssistantAnswerResponse(answer.toString().trim(), false, List.of(AssistantSources.PORTFOLIO_SUMMARY));
    }

    private static String noPortfolios() {
        return "You do not have a portfolio yet.";
    }

    private static String performerSentence(String portfolioName, HoldingAnalyticsResponse holding, boolean best) {
        String rank = best ? "best" : "worst";
        return holding.symbol()
                + " in "
                + portfolioName
                + " has the "
                + rank
                + " return at "
                + percent(holding.returnPercent())
                + "."
                + qualitySentence(holding.quality());
    }

    private static String valueSentence(AssistantContext.PortfolioSnapshot portfolio) {
        PortfolioAnalyticsResponse analytics = portfolio.analytics();
        if (analytics.mixedCurrencies() || analytics.currentValue() == null) {
            return portfolio.name() + " uses more than one currency, so its current value is not combined.";
        }
        if (analytics.holdings().isEmpty()) {
            return portfolio.name() + " has no holdings, so it has no current value.";
        }
        boolean noneValued = analytics.holdings().stream().allMatch(holding -> holding.currentValue() == null);
        if (noneValued) {
            return "The current value of " + portfolio.name() + " cannot be determined because no holding has a quote.";
        }
        String sentence = "The current value of " + portfolio.name() + " is " + money(analytics.currentValue(), analytics.currency()) + ".";
        if (analytics.unvaluedPositions() > 0) {
            sentence += " " + analytics.unvaluedPositions()
                    + (analytics.unvaluedPositions() == 1 ? " holding is" : " holdings are")
                    + " missing a quote and "
                    + (analytics.unvaluedPositions() == 1 ? "is" : "are")
                    + " not included in that value.";
        }
        return sentence;
    }

    private static String losingSentence(AssistantContext.PortfolioSnapshot portfolio) {
        PortfolioAnalyticsResponse analytics = portfolio.analytics();
        if (analytics.losers() == 0) {
            return portfolio.name() + " has no holdings that are currently losing money.";
        }
        StringBuilder sentence = new StringBuilder();
        sentence.append(portfolio.name())
                .append(" has ")
                .append(analytics.losers())
                .append(analytics.losers() == 1 ? " holding" : " holdings")
                .append(" currently losing money");
        boolean named = false;
        for (HoldingAnalyticsResponse holding : analytics.holdings()) {
            if (holding.pnl() != null && holding.pnl().signum() < 0) {
                sentence.append(named ? "; " : ": ");
                named = true;
                sentence.append(holding.symbol()).append(" at ").append(money(holding.pnl(), holding.currency()));
            }
        }
        sentence.append('.');
        return sentence.toString();
    }

    private static String uncombinedValues(List<AssistantContext.PortfolioSnapshot> portfolios) {
        StringBuilder answer = new StringBuilder();
        answer.append("These portfolios are not combined into one current value because their currencies differ. ");
        for (AssistantContext.PortfolioSnapshot portfolio : portfolios) {
            answer.append(valueSentence(portfolio)).append(' ');
        }
        return answer.toString().trim();
    }

    private static String pnlSentence(AssistantContext.PortfolioSnapshot portfolio) {
        PortfolioAnalyticsResponse analytics = portfolio.analytics();
        if (analytics.mixedCurrencies() || analytics.totalPnl() == null) {
            return portfolio.name() + " uses more than one currency, so its P&L is not combined.";
        }
        return "The total P&L for " + portfolio.name() + " is " + money(analytics.totalPnl(), analytics.currency()) + ".";
    }

    private static String returnSentence(AssistantContext.PortfolioSnapshot portfolio) {
        PortfolioAnalyticsResponse analytics = portfolio.analytics();
        if (analytics.mixedCurrencies() || analytics.returnPercent() == null) {
            if (analytics.mixedCurrencies()) {
                return portfolio.name() + " uses more than one currency, so its return is not combined.";
            }
            return "The return for " + portfolio.name() + " is not available because no holding has a valued quote.";
        }
        return "The return for " + portfolio.name() + " is " + percent(analytics.returnPercent()) + ".";
    }

    private static String sectorSentence(AssistantContext.PortfolioSnapshot portfolio) {
        PortfolioAnalyticsResponse analytics = portfolio.analytics();
        if (analytics.mixedCurrencies()) {
            return portfolio.name() + " uses more than one currency, so its sector percentages are not combined.";
        }
        if (analytics.sectorAllocations().isEmpty()) {
            return portfolio.name() + " has no sector allocation.";
        }
        StringBuilder sentence = new StringBuilder(portfolio.name()).append(" is invested in ");
        List<SectorAllocationResponse> sectors = analytics.sectorAllocations();
        for (int index = 0; index < sectors.size(); index++) {
            SectorAllocationResponse sector = sectors.get(index);
            if (index > 0) {
                sentence.append(index == sectors.size() - 1 ? " and " : ", ");
            }
            sentence.append(sector.sector()).append(" (").append(percent(sector.percentage())).append(')');
        }
        sentence.append('.');
        return sentence.toString();
    }

    private static String unvaluedSentence(String portfolioName, HoldingAnalyticsResponse holding) {
        return holding.symbol()
                + " in "
                + portfolioName
                + " has no quote, so its current value cannot be determined from the available quote.";
    }

    private static String watchlistSentence(AssistantContext.WatchlistSnapshot watchlist) {
        WatchlistChangesResponse changes = watchlist.changes();
        if (changes.firstCheck()) {
            return watchlist.name() + " has not been checked yet, so there is no change since a last check.";
        }
        if (changes.changes().isEmpty()) {
            return watchlist.name() + " has no material changes since the last check.";
        }
        StringBuilder sentence = new StringBuilder();
        sentence.append(watchlist.name())
                .append(" has ")
                .append(changes.summary().totalChanges())
                .append(changes.summary().totalChanges() == 1 ? " change" : " changes")
                .append(" since the last check.");
        int shown = 0;
        for (ChangeResponse change : changes.changes()) {
            if (shown == 8) {
                break;
            }
            sentence.append(' ')
                    .append(change.symbol())
                    .append(" on ")
                    .append(change.exchange())
                    .append(": ")
                    .append(changeLabel(change.type()))
                    .append(". ")
                    .append(bound(change.message()));
            shown++;
        }
        return sentence.toString().trim();
    }

    private static String summarySentence(AssistantContext.PortfolioSnapshot portfolio) {
        PortfolioAnalyticsResponse analytics = portfolio.analytics();
        StringBuilder sentence = new StringBuilder(portfolio.name()).append(": ");
        if (analytics.mixedCurrencies()) {
            sentence.append("this book uses more than one currency, so its value, P&L, and return are not combined. ");
        } else {
            sentence.append("current value ")
                    .append(money(analytics.currentValue(), analytics.currency()))
                    .append(", P&L ")
                    .append(money(analytics.totalPnl(), analytics.currency()))
                    .append(", return ")
                    .append(percent(analytics.returnPercent()))
                    .append(". ");
        }
        if (analytics.best() == null) {
            sentence.append("No holding is valued, so there is no best or worst performer. ");
        } else {
            sentence.append("Best is ")
                    .append(analytics.best().symbol())
                    .append(" at ")
                    .append(percent(analytics.best().returnPercent()))
                    .append(". Worst is ")
                    .append(analytics.worst().symbol())
                    .append(" at ")
                    .append(percent(analytics.worst().returnPercent()))
                    .append(". ");
        }
        sentence.append(analytics.winners())
                .append(analytics.winners() == 1 ? " winner, " : " winners, ")
                .append(analytics.losers())
                .append(analytics.losers() == 1 ? " loser, " : " losers, ")
                .append(analytics.unvaluedPositions())
                .append(" unvalued. ");
        if (!analytics.sectorAllocations().isEmpty()) {
            sentence.append(sectorSentence(portfolio)).append(' ');
        }
        for (HoldingAnalyticsResponse holding : analytics.holdings()) {
            if (holding.currentValue() == null) {
                sentence.append(unvaluedSentence(portfolio.name(), holding)).append(' ');
            }
        }
        if (analytics.realTime()) {
            sentence.append("Quotes for this book are real-time.");
        } else if (analytics.holdings().stream().anyMatch(holding -> holding.currentValue() != null)) {
            sentence.append("Quotes for this book are not all real-time.");
        }
        return sentence.toString().trim();
    }

    private static String uncombined(List<AssistantContext.PortfolioSnapshot> portfolios, String figure) {
        StringBuilder answer = new StringBuilder();
        answer.append("These portfolios are not combined into one ")
                .append(figure)
                .append(" because their currencies differ. Choose one portfolio for a single total. ");
        for (AssistantContext.PortfolioSnapshot portfolio : portfolios) {
            answer.append(pnlSentence(portfolio)).append(' ');
        }
        return answer.toString().trim();
    }

    private static String perBookReturns(List<AssistantContext.PortfolioSnapshot> portfolios) {
        StringBuilder answer = new StringBuilder();
        for (AssistantContext.PortfolioSnapshot portfolio : portfolios) {
            answer.append(returnSentence(portfolio)).append(' ');
        }
        return answer.toString().trim();
    }

    private static boolean incompatible(List<AssistantContext.PortfolioSnapshot> portfolios) {
        String currency = null;
        for (AssistantContext.PortfolioSnapshot portfolio : portfolios) {
            if (portfolio.analytics().mixedCurrencies()) {
                return true;
            }
            String next = portfolio.analytics().currency();
            if (next == null) {
                continue;
            }
            if (currency == null) {
                currency = next;
            } else if (!currency.equals(next)) {
                return true;
            }
        }
        return false;
    }

    private static BigDecimal sumPnl(List<AssistantContext.PortfolioSnapshot> portfolios) {
        BigDecimal total = new BigDecimal("0.0000");
        for (AssistantContext.PortfolioSnapshot portfolio : portfolios) {
            if (portfolio.analytics().totalPnl() != null) {
                total = total.add(portfolio.analytics().totalPnl());
            }
        }
        return total.setScale(4, RoundingMode.HALF_UP);
    }

    private static String qualitySentence(MarketDataQuality quality) {
        if (quality == null) {
            return " The quote quality was not provided, so it is not described as real-time.";
        }
        return switch (quality) {
            case REAL_TIME -> " The quote is real-time.";
            case DELAYED -> " The quote is delayed, not real-time.";
            case END_OF_DAY -> " The quote is end of day, not real-time.";
            case STALE -> " The quote is stale, not real-time.";
            case UNKNOWN -> " The quote quality is unknown, not real-time.";
        };
    }

    private static String changeLabel(ChangeType type) {
        return switch (type) {
            case PRICE_MOVE -> "Price movement";
            case VOLUME_SPIKE -> "Volume spike";
            case NEW_DAY_HIGH -> "New day high";
            case NEW_DAY_LOW -> "New day low";
            case GAP_UP -> "Gap up";
            case GAP_DOWN -> "Gap down";
            case LARGE_INTRADAY_MOVE -> "Large intraday move";
            case WATCHLIST_ADDED -> "Added to watchlist";
            case WATCHLIST_REMOVED -> "Removed from watchlist";
        };
    }

    private static String money(BigDecimal amount, String currency) {
        if (amount == null) {
            return "not available";
        }
        if (currency == null || currency.isBlank()) {
            return amount.toPlainString();
        }
        return amount.toPlainString() + " " + currency;
    }

    private static String percent(BigDecimal value) {
        if (value == null) {
            return "not available";
        }
        return value.toPlainString() + "%";
    }

    private static String bound(String message) {
        if (message == null || message.isBlank()) {
            return "";
        }
        String text = message.trim();
        if (text.length() <= MESSAGE_LIMIT) {
            return text;
        }
        return text.substring(0, MESSAGE_LIMIT);
    }

    private static boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static AssistantAnswerResponse analytics(String answer) {
        return new AssistantAnswerResponse(answer, false, List.of(AssistantSources.PORTFOLIO_ANALYTICS));
    }

    private enum Intent {
        REFUSAL,
        BEST,
        WORST,
        BOTH,
        PNL,
        VALUE,
        COUNT,
        LOSING,
        RETURN,
        SECTOR,
        UNVALUED,
        WATCHLIST,
        SUMMARY,
        UNSUPPORTED
    }
}
