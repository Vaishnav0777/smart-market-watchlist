package com.smartwatch.change;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ChangeDetectorTest {

    private static final Instant DETECTED_AT = Instant.parse("2026-01-02T00:00:00Z");
    private static final LocalDate SESSION = LocalDate.of(2024, 6, 3);
    private static final LocalDate PREVIOUS_SESSION = LocalDate.of(2024, 5, 31);

    private final ChangeDetector detector = new ChangeDetector(
            new BigDecimal("2"),
            new BigDecimal("2"),
            new BigDecimal("1"),
            new BigDecimal("2"),
            new BigDecimal("2"));

    @Test
    void doesNotInventAMoveWithoutAReference() {
        assertThat(detector.detect(List.of(subject(
                "RELIANCE",
                MembershipDelta.UNCHANGED,
                values("110", "108", "100", "112", "99", 5_000, SESSION),
                null)), DETECTED_AT)).isEmpty();
    }

    @Test
    void ignoresAnUnchangedPrice() {
        MarketSnapshot snapshot = values("100", "100", "100", "101", "99", 1_000, SESSION);

        assertThat(detector.detect(List.of(subject(
                "TCS", MembershipDelta.UNCHANGED, snapshot, snapshot)), DETECTED_AT)).isEmpty();
    }

    @Test
    void detectsAPriceIncreaseAndAPriceDecrease() {
        DetectedChange increase = only(detector.detect(List.of(subject(
                "RELIANCE",
                MembershipDelta.UNCHANGED,
                values("103", "103", "103", "120", "90", 1_000, SESSION),
                values("100", "100", "100", "120", "90", 1_000, SESSION))), DETECTED_AT));
        DetectedChange decrease = only(detector.detect(List.of(subject(
                "TCS",
                MembershipDelta.UNCHANGED,
                values("90", "90", "90", "120", "80", 1_000, SESSION),
                values("100", "100", "100", "120", "80", 1_000, SESSION))), DETECTED_AT));

        assertThat(increase.type()).isEqualTo(ChangeType.PRICE_MOVE);
        assertThat(increase.severity()).isEqualTo(ChangeSeverity.NOTABLE);
        assertThat(increase.changePercent()).isEqualByComparingTo("3.00");
        assertThat(increase.absoluteChange()).isEqualByComparingTo("3.0000");
        assertThat(increase.message()).isEqualTo("RELIANCE rose 3% since the previous observation.");

        assertThat(decrease.type()).isEqualTo(ChangeType.PRICE_MOVE);
        assertThat(decrease.severity()).isEqualTo(ChangeSeverity.HIGH);
        assertThat(decrease.changePercent()).isEqualByComparingTo("-10.00");
        assertThat(decrease.message()).isEqualTo("TCS fell 10% since the previous observation.");
    }

    @Test
    void detectsAVolumeSpikeAndIgnoresAMissingVolumeReference() {
        DetectedChange spike = only(detector.detect(List.of(subject(
                "INFY",
                MembershipDelta.UNCHANGED,
                values("100", "100", "100", "110", "90", 2_400, SESSION),
                values("100", "100", "100", "110", "90", 1_000, SESSION))), DETECTED_AT));

        assertThat(spike.type()).isEqualTo(ChangeType.VOLUME_SPIKE);
        assertThat(spike.severity()).isEqualTo(ChangeSeverity.NOTABLE);
        assertThat(spike.message()).isEqualTo("INFY volume is 2.4x its previous reference volume.");

        assertThat(detector.detect(List.of(subject(
                "INFY",
                MembershipDelta.UNCHANGED,
                values("100", "100", "100", "110", "90", 9_000, SESSION),
                new MarketSnapshot(new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("100"),
                        new BigDecimal("110"), new BigDecimal("90"), null, "INR", SESSION))), DETECTED_AT))
                .isEmpty();
        assertThat(detector.detect(List.of(subject(
                "INFY",
                MembershipDelta.UNCHANGED,
                values("100", "100", "100", "110", "90", 9_000, SESSION),
                values("100", "100", "100", "110", "90", 0L, SESSION))), DETECTED_AT)).isEmpty();
    }

    @Test
    void detectsANewHighAndANewLowOnlyWhenTheBoundaryIsKnown() {
        DetectedChange high = only(detector.detect(List.of(subject(
                "SBIN",
                MembershipDelta.UNCHANGED,
                values("110", "110", "110", "112", "100", 1_000, SESSION),
                values("110", "110", "110", "105", "100", 1_000, SESSION))), DETECTED_AT));
        DetectedChange low = only(detector.detect(List.of(subject(
                "ITC",
                MembershipDelta.UNCHANGED,
                values("90", "90", "90", "120", "90", 1_000, SESSION),
                values("90", "90", "90", "120", "95", 1_000, SESSION))), DETECTED_AT));

        assertThat(high.type()).isEqualTo(ChangeType.NEW_DAY_HIGH);
        assertThat(high.message()).isEqualTo("SBIN is trading at a new observed day high.");
        assertThat(low.type()).isEqualTo(ChangeType.NEW_DAY_LOW);
        assertThat(low.message()).isEqualTo("ITC is trading at a new observed day low.");

        MarketSnapshot noBoundary = new MarketSnapshot(
                new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("100"),
                null, null, 1_000L, "INR", SESSION);
        assertThat(detector.detect(List.of(subject(
                "SBIN",
                MembershipDelta.UNCHANGED,
                values("100", "100", "100", "112", "90", 1_000, SESSION),
                noBoundary)), DETECTED_AT)).isEmpty();
    }

    @Test
    void detectsGapsOnlyOnANewSessionWithBothPrices() {
        DetectedChange up = only(detector.detect(List.of(subject(
                "HDFCBANK",
                MembershipDelta.UNCHANGED,
                values("100", "101", "100", "120", "90", 1_000, SESSION),
                values("100", "100", "100", "120", "90", 1_000, PREVIOUS_SESSION))), DETECTED_AT));
        DetectedChange down = only(detector.detect(List.of(subject(
                "ICICIBANK",
                MembershipDelta.UNCHANGED,
                values("100", "99", "100", "120", "90", 1_000, SESSION),
                values("100", "100", "100", "120", "90", 1_000, PREVIOUS_SESSION))), DETECTED_AT));

        assertThat(up.type()).isEqualTo(ChangeType.GAP_UP);
        assertThat(up.message()).isEqualTo("HDFCBANK opened 1% above the previous close.");
        assertThat(down.type()).isEqualTo(ChangeType.GAP_DOWN);
        assertThat(down.message()).isEqualTo("ICICIBANK opened 1% below the previous close.");

        assertThat(detector.detect(List.of(subject(
                "HDFCBANK",
                MembershipDelta.UNCHANGED,
                values("100", "101", "100", "120", "90", 1_000, SESSION),
                values("100", "100", "100", "120", "90", 1_000, SESSION))), DETECTED_AT)).isEmpty();
        assertThat(detector.detect(List.of(subject(
                "HDFCBANK",
                MembershipDelta.UNCHANGED,
                new MarketSnapshot(new BigDecimal("100"), new BigDecimal("101"), null,
                        new BigDecimal("120"), new BigDecimal("90"), 1_000L, "INR", SESSION),
                values("100", "100", "100", "120", "90", 1_000, PREVIOUS_SESSION))), DETECTED_AT)).isEmpty();
    }

    @Test
    void detectsALargeIntradayMoveThatWasNotAlreadyRecorded() {
        List<DetectedChange> changes = detector.detect(List.of(subject(
                "LT",
                MembershipDelta.UNCHANGED,
                values("105", "100", "100", "120", "90", 1_000, SESSION),
                values("100", "100", "100", "120", "90", 1_000, SESSION))), DETECTED_AT);

        assertThat(changes).extracting(DetectedChange::type)
                .contains(ChangeType.LARGE_INTRADAY_MOVE, ChangeType.PRICE_MOVE);
        assertThat(changes).anyMatch(change ->
                change.type() == ChangeType.LARGE_INTRADAY_MOVE
                        && change.message().equals("LT rose 5% from the session open."));

        MarketSnapshot alreadyLarge = values("105", "100", "100", "120", "90", 1_000, SESSION);
        assertThat(detector.detect(List.of(subject(
                "LT", MembershipDelta.UNCHANGED, alreadyLarge, alreadyLarge)), DETECTED_AT))
                .noneMatch(change -> change.type() == ChangeType.LARGE_INTRADAY_MOVE);
        assertThat(detector.detect(List.of(subject(
                "LT",
                MembershipDelta.UNCHANGED,
                values("105", "100", "100", "120", "90", 1_000, SESSION),
                null)), DETECTED_AT)).isEmpty();
    }

    @Test
    void reportsEveryQualifiedChangeForOneInstrument() {
        List<DetectedChange> changes = detector.detect(List.of(subject(
                "RELIANCE",
                MembershipDelta.UNCHANGED,
                values("110", "102", "100", "112", "99", 3_000, SESSION),
                values("100", "100", "100", "105", "99", 1_000, PREVIOUS_SESSION))), DETECTED_AT);

        assertThat(changes).extracting(DetectedChange::type).containsExactly(
                ChangeType.PRICE_MOVE,
                ChangeType.VOLUME_SPIKE,
                ChangeType.NEW_DAY_HIGH,
                ChangeType.GAP_UP,
                ChangeType.LARGE_INTRADAY_MOVE);
        assertThat(ChangeHighlights.from(changes).getFirst()).isEqualTo("1 stock changed since the last check.");
        assertThat(changes).allSatisfy(change -> assertThat(change.message().toLowerCase())
                .doesNotContain("likely", "probably", "buy", "sell", "predict", "forecast"));
    }

    @Test
    void summarizesSeveralInstruments() {
        List<DetectedChange> changes = detector.detect(List.of(
                subject("AAA", MembershipDelta.UNCHANGED,
                        values("110", "110", "110", "120", "90", 1_000, SESSION),
                        values("100", "100", "100", "120", "90", 1_000, SESSION)),
                subject("BBB", MembershipDelta.UNCHANGED,
                        values("90", "90", "90", "120", "80", 1_000, SESSION),
                        values("100", "100", "100", "120", "80", 1_000, SESSION))),
                DETECTED_AT);

        assertThat(changes).extracting(DetectedChange::symbol).contains("AAA", "BBB");
        assertThat(ChangeHighlights.from(changes)).contains(
                "2 stocks changed since the last check.",
                "2 stocks had price movements.");
    }

    @Test
    void reportsMembershipWithoutPredictingAPrice() {
        List<DetectedChange> added = detector.detect(List.of(subject(
                "MARUTI",
                MembershipDelta.ADDED,
                values("100", "100", "100", "110", "90", 1_000, SESSION),
                null)), DETECTED_AT);
        List<DetectedChange> removed = detector.detect(List.of(subject(
                "BHARTIARTL",
                MembershipDelta.REMOVED,
                values("50", "50", "50", "80", "40", 9_000, SESSION),
                values("100", "100", "100", "110", "90", 1_000, SESSION))), DETECTED_AT);

        assertThat(added).extracting(DetectedChange::type).containsExactly(ChangeType.WATCHLIST_ADDED);
        assertThat(added.getFirst().message()).isEqualTo("MARUTI was added to this watchlist.");
        assertThat(removed).extracting(DetectedChange::type).containsExactly(ChangeType.WATCHLIST_REMOVED);
        assertThat(removed.getFirst().currentValue()).isNull();
        assertThat(removed.getFirst().message()).isEqualTo("BHARTIARTL was removed from this watchlist.");
    }

    private static DetectedChange only(List<DetectedChange> changes) {
        assertThat(changes).hasSize(1);
        return changes.getFirst();
    }

    private static ChangeSubject subject(
            String symbol,
            MembershipDelta membership,
            MarketSnapshot current,
            MarketSnapshot reference) {
        return new ChangeSubject(UUID.randomUUID(), symbol, "NSE", membership, current, reference);
    }

    private static MarketSnapshot values(
            String price,
            String open,
            String previousClose,
            String high,
            String low,
            long volume,
            LocalDate session) {
        return new MarketSnapshot(
                new BigDecimal(price),
                new BigDecimal(open),
                new BigDecimal(previousClose),
                new BigDecimal(high),
                new BigDecimal(low),
                volume,
                "INR",
                session);
    }
}
