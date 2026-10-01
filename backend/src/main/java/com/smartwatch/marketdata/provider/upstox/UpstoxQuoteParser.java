package com.smartwatch.marketdata.provider.upstox;

import com.smartwatch.marketdata.model.MarketDataQuality;
import com.smartwatch.marketdata.model.MarketDataSource;
import com.smartwatch.marketdata.model.Quote;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Maps an Upstox V3 full-quote payload into {@link Quote}.
 *
 * <p>Three times stay separate:
 * <ul>
 * <li>{@code marketTimestamp} is {@code last_trade_time}: when the trade
 * represented by {@code last_price} occurred, as epoch milliseconds.
 * <li>{@code observedAt} is the server clock after this parser has accepted
 * the response. It is not a market time.
 * <li>{@code timestamp} is when Upstox generated the snapshot. It is not
 * stored. It is compared with {@code observedAt} only to choose quality.
 * </ul>
 *
 * <p>A snapshot older than {@code staleAfter} before {@code observedAt} is
 * {@link MarketDataQuality#STALE}. A snapshot inside that window is
 * {@link MarketDataQuality#REAL_TIME}, including when the last trade is old.
 * V3 documents the snapshot as taken from the exchange at request time and
 * does not mark it delayed. A missing snapshot time is
 * {@link MarketDataQuality#UNKNOWN}. A missing {@code last_trade_time}
 * is not replaced with {@code timestamp}.
 *
 * <p>{@code prev_close_price} is the previous session close. {@code ohlc.close}
 * is not used as the previous close. Day volume is the top-level {@code volume},
 * not {@code ohlc.volume}.
 */
final class UpstoxQuoteParser {

    static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Kolkata");

    private static final int MONEY_SCALE = 4;

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .build();

    private UpstoxQuoteParser() {
    }

    static List<Quote> parse(
            String body,
            List<UpstoxInstrumentMapping> requested,
            Clock clock,
            Duration staleAfter) {
        List<AcceptedQuote> accepted = readAccepted(body, requested);
        if (accepted.isEmpty()) {
            return List.of();
        }
        Instant observedAt = clock.instant();
        List<Quote> quotes = new ArrayList<>();
        for (AcceptedQuote acceptedQuote : accepted) {
            quotes.add(acceptedQuote.toQuote(observedAt, staleAfter));
        }
        return List.copyOf(quotes);
    }

    private static List<AcceptedQuote> readAccepted(String body, List<UpstoxInstrumentMapping> requested) {
        JsonNode root = readTree(body);
        if (root == null || !root.isObject()) {
            throw MarketDataUnavailableException.invalidResponse();
        }
        JsonNode status = root.get("status");
        if (status == null || !status.isString() || !"success".equals(status.asString())) {
            throw MarketDataUnavailableException.invalidResponse();
        }
        JsonNode data = root.get("data");
        if (data == null || data.isNull()) {
            return List.of();
        }
        if (!data.isObject()) {
            throw MarketDataUnavailableException.invalidResponse();
        }

        List<AcceptedQuote> accepted = new ArrayList<>();
        for (UpstoxInstrumentMapping mapping : requested) {
            JsonNode payload = findPayload(data, mapping);
            if (payload == null) {
                continue;
            }
            AcceptedQuote quote = accept(payload, mapping);
            if (quote != null) {
                accepted.add(quote);
            }
        }
        return List.copyOf(accepted);
    }

    private static JsonNode readTree(String body) {
        if (body == null || body.isBlank()) {
            throw MarketDataUnavailableException.invalidResponse();
        }
        try {
            return MAPPER.readTree(body);
        } catch (JacksonException exception) {
            throw MarketDataUnavailableException.invalidResponse();
        }
    }

    private static JsonNode findPayload(JsonNode data, UpstoxInstrumentMapping mapping) {
        for (JsonNode payload : data) {
            if (!payload.isObject()) {
                throw MarketDataUnavailableException.invalidResponse();
            }
            if (sameKey(text(payload, "instrument_token"), mapping.externalInstrumentKey())) {
                return payload;
            }
        }
        return null;
    }

    private static AcceptedQuote accept(JsonNode payload, UpstoxInstrumentMapping mapping) {
        BigDecimal price = money(payload.get("last_price"));
        Long volume = integral(payload.get("volume"));
        Instant marketTimestamp = epochMillis(payload.get("last_trade_time"));
        if (price == null || volume == null || marketTimestamp == null) {
            return null;
        }
        JsonNode ohlc = payload.get("ohlc");
        if (ohlc != null && !ohlc.isNull() && !ohlc.isObject()) {
            throw MarketDataUnavailableException.invalidResponse();
        }
        return new AcceptedQuote(
                mapping,
                price,
                money(payload.get("prev_close_price")),
                ohlc == null || ohlc.isNull() ? null : money(ohlc.get("open")),
                ohlc == null || ohlc.isNull() ? null : money(ohlc.get("high")),
                ohlc == null || ohlc.isNull() ? null : money(ohlc.get("low")),
                volume,
                marketTimestamp,
                snapshotTimestamp(payload.get("timestamp")));
    }

    /**
     * Snapshot freshness uses the Upstox {@code timestamp} only. An old
     * {@code last_trade_time} does not make a fresh snapshot stale.
     */
    private static MarketDataQuality quality(Instant snapshotTimestamp, Instant observedAt, Duration staleAfter) {
        if (snapshotTimestamp == null) {
            return MarketDataQuality.UNKNOWN;
        }
        Duration window = staleAfter == null ? UpstoxProperties.DEFAULT_STALE_AFTER : staleAfter;
        if (observedAt != null && snapshotTimestamp.isBefore(observedAt.minus(window))) {
            return MarketDataQuality.STALE;
        }
        return MarketDataQuality.REAL_TIME;
    }

    private static Instant snapshotTimestamp(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isString() || node.asString().isBlank()) {
            throw MarketDataUnavailableException.invalidResponse();
        }
        Instant snapshot = isoTimestamp(node);
        if (snapshot == null) {
            throw MarketDataUnavailableException.invalidResponse();
        }
        return snapshot;
    }

    private static Instant epochMillis(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return Instant.ofEpochMilli(node.longValue());
        }
        if (!node.isString() || node.asString().isBlank()) {
            return null;
        }
        try {
            return Instant.ofEpochMilli(Long.parseLong(node.asString().trim()));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static Instant isoTimestamp(JsonNode node) {
        if (node == null || node.isNull() || !node.isString() || node.asString().isBlank()) {
            return null;
        }
        try {
            return Instant.parse(node.asString().trim());
        } catch (DateTimeParseException iso) {
            try {
                return java.time.OffsetDateTime.parse(node.asString().trim()).toInstant();
            } catch (DateTimeParseException offset) {
                return null;
            }
        }
    }

    private static BigDecimal money(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isNumber()) {
            throw MarketDataUnavailableException.invalidResponse();
        }
        return node.decimalValue().setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static Long integral(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isNumber()) {
            throw MarketDataUnavailableException.invalidResponse();
        }
        BigDecimal value = node.decimalValue();
        if (value.stripTrailingZeros().scale() > 0) {
            throw MarketDataUnavailableException.invalidResponse();
        }
        try {
            return value.longValueExact();
        } catch (ArithmeticException exception) {
            throw MarketDataUnavailableException.invalidResponse();
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.isString()) {
            return null;
        }
        return value.asString();
    }

    private static boolean sameKey(String left, String right) {
        return left != null && right != null && left.equals(right);
    }

    private static String currency(String instrumentKey) {
        String prefix = instrumentKey == null ? "" : instrumentKey.toUpperCase(Locale.ROOT);
        if (prefix.startsWith("NSE_") || prefix.startsWith("BSE_")) {
            return "INR";
        }
        return "UNK";
    }

    private record AcceptedQuote(
            UpstoxInstrumentMapping mapping,
            BigDecimal price,
            BigDecimal previousClose,
            BigDecimal open,
            BigDecimal high,
            BigDecimal low,
            long volume,
            Instant marketTimestamp,
            Instant snapshotTimestamp) {

        private Quote toQuote(Instant observedAt, Duration staleAfter) {
            return new Quote(
                    mapping.symbol(),
                    mapping.companyName(),
                    mapping.exchange(),
                    mapping.sector(),
                    price,
                    previousClose,
                    open,
                    high,
                    low,
                    volume,
                    marketTimestamp,
                    observedAt,
                    MarketDataSource.UPSTOX,
                    quality(snapshotTimestamp, observedAt, staleAfter),
                    currency(mapping.externalInstrumentKey()),
                    LocalDate.ofInstant(marketTimestamp, MARKET_ZONE),
                    false);
        }
    }
}
