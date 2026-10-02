package com.smartwatch.portfolio.service;

import com.smartwatch.marketdata.model.MarketDataQuality;
import com.smartwatch.marketdata.model.MarketDataSource;
import com.smartwatch.portfolio.dto.HoldingAnalyticsResponse;
import com.smartwatch.portfolio.dto.InstrumentAllocationResponse;
import com.smartwatch.portfolio.dto.PortfolioAnalyticsResponse;
import com.smartwatch.portfolio.dto.SectorAllocationResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Portfolio figures from stored holdings and quotes already matched to them.
 * This class does not load data and does not forecast a price.
 */
public final class PortfolioAnalytics {

    private static final int MONEY_SCALE = 4;
    private static final int PERCENT_SCALE = 2;
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal ZERO_MONEY = new BigDecimal("0.0000");

    private PortfolioAnalytics() {
    }

    public static PortfolioAnalyticsResponse calculate(UUID portfolioId, List<PositionInput> positions) {
        List<Row> rows = new ArrayList<>();
        for (PositionInput position : positions) {
            rows.add(Row.from(position));
        }
        rows.sort(Comparator.comparing(Row::symbol).thenComparing(Row::exchange));

        List<Row> valued = rows.stream().filter(Row::valued).toList();
        boolean mixedCurrencies = currencies(valued).size() > 1;
        String currency = mixedCurrencies ? null : singleCurrency(valued);
        boolean realTime = !valued.isEmpty()
                && valued.stream().allMatch(row -> row.quality == MarketDataQuality.REAL_TIME);

        BigDecimal totalInvested = sum(rows.stream().map(row -> row.invested).toList());
        BigDecimal valuedInvested = sum(valued.stream().map(row -> row.invested).toList());
        BigDecimal currentValue = sum(valued.stream().map(row -> row.currentValue).toList());
        BigDecimal totalPnl = currentValue.subtract(valuedInvested).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal returnPercent = valuedInvested.signum() == 0 ? null : percent(totalPnl, valuedInvested);

        int winners = 0;
        int losers = 0;
        int unvalued = 0;
        for (Row row : rows) {
            if (!row.valued) {
                unvalued++;
            } else if (row.pnl.signum() > 0) {
                winners++;
            } else if (row.pnl.signum() < 0) {
                losers++;
            }
        }

        List<HoldingAnalyticsResponse> holdings = rows.stream().map(Row::toResponse).toList();
        HoldingAnalyticsResponse best = pick(valued, true);
        HoldingAnalyticsResponse worst = pick(valued, false);

        if (mixedCurrencies) {
            return new PortfolioAnalyticsResponse(
                    portfolioId,
                    null,
                    true,
                    realTime,
                    null,
                    null,
                    null,
                    null,
                    winners,
                    losers,
                    unvalued,
                    best,
                    worst,
                    List.of(),
                    List.of(),
                    holdings);
        }

        return new PortfolioAnalyticsResponse(
                portfolioId,
                currency,
                false,
                realTime,
                totalInvested,
                rows.isEmpty() ? ZERO_MONEY : currentValue,
                rows.isEmpty() ? ZERO_MONEY : totalPnl,
                returnPercent,
                winners,
                losers,
                unvalued,
                best,
                worst,
                sectorAllocations(rows, totalInvested),
                instrumentAllocations(rows, totalInvested),
                holdings);
    }

    private static HoldingAnalyticsResponse pick(List<Row> valued, boolean best) {
        Comparator<Row> order = best
                ? Comparator.comparing((Row row) -> row.returnPercent).reversed().thenComparing(Row::symbol)
                : Comparator.comparing((Row row) -> row.returnPercent).thenComparing(Row::symbol);
        return valued.stream().min(order).map(Row::toResponse).orElse(null);
    }

    private static List<SectorAllocationResponse> sectorAllocations(List<Row> rows, BigDecimal totalInvested) {
        if (totalInvested.signum() == 0) {
            return List.of();
        }
        Map<String, BigDecimal> investedBySector = new LinkedHashMap<>();
        for (Row row : rows) {
            investedBySector.merge(row.sector, row.invested, BigDecimal::add);
        }
        return investedBySector.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new SectorAllocationResponse(
                        entry.getKey(),
                        entry.getValue().setScale(MONEY_SCALE, RoundingMode.HALF_UP),
                        percent(entry.getValue(), totalInvested)))
                .toList();
    }

    private static List<InstrumentAllocationResponse> instrumentAllocations(List<Row> rows, BigDecimal totalInvested) {
        if (totalInvested.signum() == 0) {
            return List.of();
        }
        return rows.stream()
                .sorted(Comparator.comparing(Row::symbol).thenComparing(Row::exchange))
                .map(row -> new InstrumentAllocationResponse(
                        row.instrumentId,
                        row.symbol,
                        row.exchange,
                        row.sector,
                        row.invested,
                        percent(row.invested, totalInvested)))
                .toList();
    }

    private static List<String> currencies(List<Row> valued) {
        return valued.stream().map(row -> row.currency).distinct().toList();
    }

    private static String singleCurrency(List<Row> valued) {
        if (valued.isEmpty()) {
            return null;
        }
        return valued.get(0).currency;
    }

    private static BigDecimal sum(List<BigDecimal> amounts) {
        BigDecimal total = ZERO_MONEY;
        for (BigDecimal amount : amounts) {
            total = total.add(amount);
        }
        return total.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal money(BigDecimal quantity, BigDecimal price) {
        return quantity.multiply(price).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal percent(BigDecimal part, BigDecimal whole) {
        return part.multiply(HUNDRED).divide(whole, PERCENT_SCALE, RoundingMode.HALF_UP);
    }

    private static String currencyOf(QuoteSnapshot quote) {
        if (quote == null || quote.currency() == null || quote.currency().isBlank()) {
            return null;
        }
        return quote.currency();
    }

    public record PositionInput(
            UUID instrumentId,
            String symbol,
            String exchange,
            String sector,
            BigDecimal quantity,
            BigDecimal averageBuyPrice,
            QuoteSnapshot quote) {
    }

    /**
     * The quote fields analytics is allowed to use. A null snapshot means no match.
     */
    public record QuoteSnapshot(
            BigDecimal price,
            String currency,
            MarketDataQuality quality,
            MarketDataSource source,
            Instant marketTimestamp,
            Instant observedAt) {
    }

    private static final class Row {
        private final UUID instrumentId;
        private final String symbol;
        private final String exchange;
        private final String sector;
        private final String currency;
        private final BigDecimal invested;
        private final BigDecimal currentValue;
        private final BigDecimal pnl;
        private final BigDecimal returnPercent;
        private final boolean valued;
        private final MarketDataQuality quality;
        private final MarketDataSource source;
        private final Instant marketTimestamp;
        private final Instant observedAt;

        private Row(
                UUID instrumentId,
                String symbol,
                String exchange,
                String sector,
                String currency,
                BigDecimal invested,
                BigDecimal currentValue,
                BigDecimal pnl,
                BigDecimal returnPercent,
                boolean valued,
                MarketDataQuality quality,
                MarketDataSource source,
                Instant marketTimestamp,
                Instant observedAt) {
            this.instrumentId = instrumentId;
            this.symbol = symbol;
            this.exchange = exchange;
            this.sector = sector;
            this.currency = currency;
            this.invested = invested;
            this.currentValue = currentValue;
            this.pnl = pnl;
            this.returnPercent = returnPercent;
            this.valued = valued;
            this.quality = quality;
            this.source = source;
            this.marketTimestamp = marketTimestamp;
            this.observedAt = observedAt;
        }

        private static Row from(PositionInput position) {
            BigDecimal invested = money(position.quantity(), position.averageBuyPrice());
            QuoteSnapshot quote = position.quote();
            boolean valued = quote != null && quote.price() != null;
            if (!valued) {
                return new Row(
                        position.instrumentId(),
                        position.symbol(),
                        position.exchange(),
                        position.sector(),
                        null,
                        invested,
                        null,
                        null,
                        null,
                        false,
                        null,
                        null,
                        null,
                        null);
            }
            BigDecimal currentValue = money(position.quantity(), quote.price());
            BigDecimal pnl = currentValue.subtract(invested).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            return new Row(
                    position.instrumentId(),
                    position.symbol(),
                    position.exchange(),
                    position.sector(),
                    currencyOf(quote),
                    invested,
                    currentValue,
                    pnl,
                    percent(pnl, invested),
                    true,
                    quote.quality(),
                    quote.source(),
                    quote.marketTimestamp(),
                    quote.observedAt());
        }

        private HoldingAnalyticsResponse toResponse() {
            return new HoldingAnalyticsResponse(
                    instrumentId,
                    symbol,
                    exchange,
                    sector,
                    currency,
                    invested,
                    currentValue,
                    pnl,
                    returnPercent,
                    quality,
                    source,
                    marketTimestamp,
                    observedAt);
        }

        private String symbol() {
            return symbol;
        }

        private String exchange() {
            return exchange;
        }

        private boolean valued() {
            return valued;
        }
    }
}
