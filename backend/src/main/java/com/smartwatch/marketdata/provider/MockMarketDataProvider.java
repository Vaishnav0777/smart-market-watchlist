package com.smartwatch.marketdata.provider;

import com.smartwatch.marketdata.model.HistoricalBar;
import com.smartwatch.marketdata.model.MarketDataQuality;
import com.smartwatch.marketdata.model.MarketDataSource;
import com.smartwatch.marketdata.model.Quote;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Deterministic synthetic market data for local development.
 *
 * <p>Every figure in this class is an invented fixture. The shared sample
 * clock is frozen. Nothing here is a live or historical NSE or BSE price,
 * and these values must never be presented as real market data.
 */
public class MockMarketDataProvider implements MarketDataProvider {

    public static final String SYNTHETIC_DATA_NOTICE =
            "SYNTHETIC DEVELOPMENT DATA. These values are invented fixtures for local development. "
                    + "They are not current or historical NSE or BSE prices and must never be presented as real market data.";

    public static final MarketDataSource SOURCE = MarketDataSource.MOCK;

    /**
     * The frozen catalog is a closed sample session, not a live tick.
     */
    public static final MarketDataQuality SAMPLE_QUALITY = MarketDataQuality.END_OF_DAY;

    public static final String CURRENCY = "INR";

    /**
     * Listing label stored on each synthetic instrument. It does not mean
     * the process is connected to that exchange.
     */
    public static final String LISTING_EXCHANGE = "NSE";

    public static final LocalDate SAMPLE_SESSION_DATE = LocalDate.of(2024, 6, 3);

    public static final Instant SAMPLE_TIMESTAMP = ZonedDateTime.of(
            2024, 6, 3, 15, 30, 0, 0, ZoneId.of("Asia/Kolkata")).toInstant();

    private static final int SESSION_COUNT = 5;

    private static final BigDecimal[] CLOSE_FACTORS = {
            new BigDecimal("0.976"),
            new BigDecimal("0.983"),
            new BigDecimal("0.988"),
            new BigDecimal("0.992"),
            new BigDecimal("1.000")
    };

    private static final List<InstrumentSeed> INSTRUMENTS = List.of(
            new InstrumentSeed("RELIANCE", "Reliance Industries", "Energy", "2500.00", 3_200_000L),
            new InstrumentSeed("TCS", "Tata Consultancy Services", "Information Technology", "4000.50", 1_500_000L),
            new InstrumentSeed("INFY", "Infosys", "Information Technology", "1800.25", 4_100_000L),
            new InstrumentSeed("HDFCBANK", "HDFC Bank", "Financial Services", "1650.75", 5_200_000L),
            new InstrumentSeed("ICICIBANK", "ICICI Bank", "Financial Services", "1250.00", 6_100_000L),
            new InstrumentSeed("SBIN", "State Bank of India", "Financial Services", "820.50", 8_900_000L),
            new InstrumentSeed("ITC", "ITC", "Consumer Staples", "460.25", 7_300_000L),
            new InstrumentSeed("BHARTIARTL", "Bharti Airtel", "Communication Services", "1550.00", 2_800_000L),
            new InstrumentSeed("LT", "Larsen & Toubro", "Industrials", "3600.75", 980_000L),
            new InstrumentSeed("MARUTI", "Maruti Suzuki India", "Consumer Discretionary", "12800.00", 640_000L)
    );

    private final Map<String, Quote> quotesBySymbol;
    private final Map<String, List<HistoricalBar>> barsBySymbol;
    private final Map<String, Quote> replacements = new HashMap<>();

    public MockMarketDataProvider() {
        if (CLOSE_FACTORS.length != SESSION_COUNT) {
            throw new IllegalStateException("Sample session factors do not match the session count");
        }

        List<LocalDate> sessions = sampleSessions();
        Map<String, Quote> quotes = new LinkedHashMap<>();
        Map<String, List<HistoricalBar>> bars = new LinkedHashMap<>();

        for (InstrumentSeed instrument : INSTRUMENTS) {
            List<HistoricalBar> history = buildHistory(instrument, sessions);
            bars.put(instrument.symbol(), List.copyOf(history));
            quotes.put(instrument.symbol(), quoteFrom(instrument, history));
        }

        this.quotesBySymbol = Map.copyOf(quotes);
        this.barsBySymbol = Map.copyOf(bars);
    }

    @Override
    public MarketDataSource source() {
        return SOURCE;
    }

    /**
     * Replaces one catalog quote on this instance. Tests use a dedicated
     * instance so the shared application provider stays on the frozen catalog.
     */
    void replaceQuote(Quote quote) {
        if (quote == null || quote.symbol() == null || quote.symbol().isBlank()) {
            throw new IllegalArgumentException("quote symbol must not be blank");
        }
        replacements.put(normalize(quote.symbol()), quote);
    }

    void clearReplacements() {
        replacements.clear();
    }

    @Override
    public Optional<Quote> getQuote(String symbol) {
        String key = normalize(symbol);
        Quote replacement = replacements.get(key);
        if (replacement != null) {
            return Optional.of(replacement);
        }
        return Optional.ofNullable(quotesBySymbol.get(key));
    }

    @Override
    public List<Quote> getQuotes(List<String> symbols) {
        if (symbols == null) {
            throw new IllegalArgumentException("symbols must not be null");
        }

        List<Quote> quotes = new ArrayList<>();
        for (String symbol : symbols) {
            getQuote(symbol).ifPresent(quotes::add);
        }
        return List.copyOf(quotes);
    }

    @Override
    public List<Quote> listQuotes() {
        List<Quote> quotes = new ArrayList<>();
        for (InstrumentSeed instrument : INSTRUMENTS) {
            getQuote(instrument.symbol()).ifPresent(quotes::add);
        }
        return List.copyOf(quotes);
    }

    @Override
    public List<HistoricalBar> getHistoricalBars(String symbol, LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("from and to must not be null");
        }
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("from must not be after to");
        }

        List<HistoricalBar> bars = barsBySymbol.get(normalize(symbol));
        if (bars == null) {
            return List.of();
        }

        return bars.stream()
                .filter(bar -> !bar.date().isBefore(from) && !bar.date().isAfter(to))
                .toList();
    }

    private static Quote quoteFrom(InstrumentSeed instrument, List<HistoricalBar> history) {
        HistoricalBar latest = history.get(history.size() - 1);
        HistoricalBar previous = history.get(history.size() - 2);
        return new Quote(
                instrument.symbol(),
                instrument.companyName(),
                LISTING_EXCHANGE,
                instrument.sector(),
                latest.close(),
                previous.close(),
                latest.open(),
                latest.high(),
                latest.low(),
                latest.volume(),
                SAMPLE_TIMESTAMP,
                null,
                SOURCE,
                SAMPLE_QUALITY,
                CURRENCY,
                SAMPLE_SESSION_DATE,
                true);
    }

    private static List<HistoricalBar> buildHistory(InstrumentSeed instrument, List<LocalDate> sessions) {
        BigDecimal price = new BigDecimal(instrument.price());
        List<HistoricalBar> bars = new ArrayList<>();

        for (int index = 0; index < sessions.size(); index++) {
            BigDecimal close = price.multiply(CLOSE_FACTORS[index]).setScale(2, RoundingMode.HALF_UP);
            boolean latestSession = index == sessions.size() - 1;
            long volume = latestSession
                    ? instrument.volume()
                    : Math.round(instrument.volume() * (0.85 + (0.03 * index)));

            bars.add(new HistoricalBar(
                    instrument.symbol(),
                    sessions.get(index),
                    close.multiply(new BigDecimal("0.997")).setScale(2, RoundingMode.HALF_UP),
                    close.multiply(new BigDecimal("1.008")).setScale(2, RoundingMode.HALF_UP),
                    close.multiply(new BigDecimal("0.991")).setScale(2, RoundingMode.HALF_UP),
                    close,
                    volume,
                    true));
        }

        return bars;
    }

    private static List<LocalDate> sampleSessions() {
        List<LocalDate> sessions = new ArrayList<>();
        LocalDate cursor = SAMPLE_SESSION_DATE;
        while (sessions.size() < SESSION_COUNT) {
            DayOfWeek day = cursor.getDayOfWeek();
            if (day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY) {
                sessions.add(cursor);
            }
            cursor = cursor.minusDays(1);
        }
        return sessions.reversed();
    }

    private static String normalize(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol must not be blank");
        }
        return symbol.trim().toUpperCase(Locale.ROOT);
    }

    private record InstrumentSeed(String symbol, String companyName, String sector, String price, long volume) {
    }
}
