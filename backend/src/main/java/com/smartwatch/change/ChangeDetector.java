package com.smartwatch.change;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Compares a current snapshot with an optional reference.
 * Every result describes an observation. Nothing here forecasts a price.
 */
@Component
@EnableConfigurationProperties(ChangeThresholdProperties.class)
public class ChangeDetector {

    private static final int PERCENT_SCALE = 2;
    private static final int PRICE_SCALE = 4;

    private final BigDecimal priceMovePercent;
    private final BigDecimal volumeSpikeMultiple;
    private final BigDecimal gapPercent;
    private final BigDecimal intradayMovePercent;
    private final BigDecimal highSeverityMultiple;

    @Autowired
    public ChangeDetector(ChangeThresholdProperties properties) {
        this(
                properties.getPriceMovePercent(),
                properties.getVolumeSpikeMultiple(),
                properties.getGapPercent(),
                properties.getIntradayMovePercent(),
                properties.getHighSeverityMultiple());
    }

    public ChangeDetector(
            BigDecimal priceMovePercent,
            BigDecimal volumeSpikeMultiple,
            BigDecimal gapPercent,
            BigDecimal intradayMovePercent,
            BigDecimal highSeverityMultiple) {
        this.priceMovePercent = requirePositive(priceMovePercent, "priceMovePercent");
        this.volumeSpikeMultiple = requirePositive(volumeSpikeMultiple, "volumeSpikeMultiple");
        this.gapPercent = requirePositive(gapPercent, "gapPercent");
        this.intradayMovePercent = requirePositive(intradayMovePercent, "intradayMovePercent");
        this.highSeverityMultiple = requirePositive(highSeverityMultiple, "highSeverityMultiple");
    }

    public List<DetectedChange> detect(List<ChangeSubject> subjects, Instant detectedAt) {
        List<DetectedChange> changes = new ArrayList<>();
        for (ChangeSubject subject : subjects) {
            if (subject.membership() == MembershipDelta.REMOVED) {
                changes.add(membership(subject, ChangeType.WATCHLIST_REMOVED, detectedAt,
                        subject.symbol() + " was removed from this watchlist."));
                continue;
            }
            if (subject.membership() == MembershipDelta.ADDED) {
                changes.add(membership(subject, ChangeType.WATCHLIST_ADDED, detectedAt,
                        subject.symbol() + " was added to this watchlist."));
            }
            detectMarket(subject, detectedAt, changes);
        }
        changes.sort(Comparator.comparing(DetectedChange::symbol).thenComparing(change -> change.type().ordinal()));
        return List.copyOf(changes);
    }

    private void detectMarket(ChangeSubject subject, Instant detectedAt, List<DetectedChange> changes) {
        MarketSnapshot current = subject.current();
        MarketSnapshot reference = subject.reference();
        if (current == null) {
            return;
        }
        priceMove(subject, current, reference, detectedAt, changes);
        volumeSpike(subject, current, reference, detectedAt, changes);
        newExtreme(subject, current, reference, detectedAt, changes, true);
        newExtreme(subject, current, reference, detectedAt, changes, false);
        gap(subject, current, reference, detectedAt, changes);
        intraday(subject, current, reference, detectedAt, changes);
    }

    private void priceMove(
            ChangeSubject subject,
            MarketSnapshot current,
            MarketSnapshot reference,
            Instant detectedAt,
            List<DetectedChange> changes) {
        if (current.price() == null || reference == null || reference.price() == null) {
            return;
        }
        BigDecimal percent = percentChange(current.price(), reference.price());
        if (percent == null || percent.abs().compareTo(priceMovePercent) < 0) {
            return;
        }
        String direction = percent.signum() >= 0 ? "rose" : "fell";
        changes.add(market(
                subject,
                ChangeType.PRICE_MOVE,
                severity(percent.abs(), priceMovePercent),
                current.price(),
                reference.price(),
                difference(current.price(), reference.price()),
                percent,
                current.currency(),
                detectedAt,
                subject.symbol() + " " + direction + " " + format(percent) + "% since the previous observation."));
    }

    private void volumeSpike(
            ChangeSubject subject,
            MarketSnapshot current,
            MarketSnapshot reference,
            Instant detectedAt,
            List<DetectedChange> changes) {
        if (current.volume() == null || reference == null || reference.volume() == null || reference.volume() <= 0) {
            return;
        }
        BigDecimal currentVolume = BigDecimal.valueOf(current.volume());
        BigDecimal referenceVolume = BigDecimal.valueOf(reference.volume());
        BigDecimal multiple = currentVolume.divide(referenceVolume, PERCENT_SCALE, RoundingMode.HALF_UP);
        if (multiple.compareTo(volumeSpikeMultiple) < 0) {
            return;
        }
        BigDecimal percent = percentChange(currentVolume, referenceVolume);
        changes.add(market(
                subject,
                ChangeType.VOLUME_SPIKE,
                severity(multiple, volumeSpikeMultiple),
                currentVolume,
                referenceVolume,
                currentVolume.subtract(referenceVolume),
                percent,
                null,
                detectedAt,
                subject.symbol() + " volume is " + format(multiple) + "x its previous reference volume."));
    }

    private void newExtreme(
            ChangeSubject subject,
            MarketSnapshot current,
            MarketSnapshot reference,
            Instant detectedAt,
            List<DetectedChange> changes,
            boolean high) {
        if (current.price() == null || reference == null) {
            return;
        }
        BigDecimal boundary = high ? reference.dayHigh() : reference.dayLow();
        if (boundary == null) {
            return;
        }
        boolean breached = high
                ? current.price().compareTo(boundary) > 0
                : current.price().compareTo(boundary) < 0;
        if (!breached) {
            return;
        }
        ChangeType type = high ? ChangeType.NEW_DAY_HIGH : ChangeType.NEW_DAY_LOW;
        String extreme = high ? "high" : "low";
        changes.add(market(
                subject,
                type,
                ChangeSeverity.NOTABLE,
                current.price(),
                boundary,
                difference(current.price(), boundary),
                percentChange(current.price(), boundary),
                current.currency(),
                detectedAt,
                subject.symbol() + " is trading at a new observed day " + extreme + "."));
    }

    private void gap(
            ChangeSubject subject,
            MarketSnapshot current,
            MarketSnapshot reference,
            Instant detectedAt,
            List<DetectedChange> changes) {
        if (reference == null || current.open() == null || current.previousClose() == null) {
            return;
        }
        if (sameSession(current.sessionDate(), reference.sessionDate())) {
            return;
        }
        BigDecimal percent = percentChange(current.open(), current.previousClose());
        if (percent == null || percent.abs().compareTo(gapPercent) < 0) {
            return;
        }
        boolean up = percent.signum() >= 0;
        changes.add(market(
                subject,
                up ? ChangeType.GAP_UP : ChangeType.GAP_DOWN,
                severity(percent.abs(), gapPercent),
                current.open(),
                current.previousClose(),
                difference(current.open(), current.previousClose()),
                percent,
                current.currency(),
                detectedAt,
                subject.symbol() + " opened " + format(percent) + "% "
                        + (up ? "above" : "below") + " the previous close."));
    }

    private void intraday(
            ChangeSubject subject,
            MarketSnapshot current,
            MarketSnapshot reference,
            Instant detectedAt,
            List<DetectedChange> changes) {
        if (reference == null || current.price() == null || current.open() == null) {
            return;
        }
        BigDecimal percent = percentChange(current.price(), current.open());
        if (percent == null || percent.abs().compareTo(intradayMovePercent) < 0) {
            return;
        }
        if (sameSession(current.sessionDate(), reference.sessionDate())
                && referenceAlreadyMoved(reference, percent.signum())) {
            return;
        }
        String direction = percent.signum() >= 0 ? "rose" : "fell";
        changes.add(market(
                subject,
                ChangeType.LARGE_INTRADAY_MOVE,
                severity(percent.abs(), intradayMovePercent),
                current.price(),
                current.open(),
                difference(current.price(), current.open()),
                percent,
                current.currency(),
                detectedAt,
                subject.symbol() + " " + direction + " " + format(percent) + "% from the session open."));
    }

    private boolean referenceAlreadyMoved(MarketSnapshot reference, int sign) {
        if (reference.price() == null || reference.open() == null) {
            return false;
        }
        BigDecimal prior = percentChange(reference.price(), reference.open());
        if (prior == null || prior.signum() != sign) {
            return false;
        }
        return prior.abs().compareTo(intradayMovePercent) >= 0;
    }

    private ChangeSeverity severity(BigDecimal magnitude, BigDecimal threshold) {
        BigDecimal highAt = threshold.multiply(highSeverityMultiple);
        if (magnitude.compareTo(highAt) >= 0) {
            return ChangeSeverity.HIGH;
        }
        return ChangeSeverity.NOTABLE;
    }

    private static DetectedChange membership(
            ChangeSubject subject,
            ChangeType type,
            Instant detectedAt,
            String message) {
        return new DetectedChange(
                subject.instrumentId(),
                subject.symbol(),
                subject.exchange(),
                type,
                ChangeSeverity.NOTABLE,
                null,
                null,
                null,
                null,
                null,
                detectedAt,
                message);
    }

    private static DetectedChange market(
            ChangeSubject subject,
            ChangeType type,
            ChangeSeverity severity,
            BigDecimal currentValue,
            BigDecimal referenceValue,
            BigDecimal absoluteChange,
            BigDecimal changePercent,
            String currency,
            Instant detectedAt,
            String message) {
        return new DetectedChange(
                subject.instrumentId(),
                subject.symbol(),
                subject.exchange(),
                type,
                severity,
                currentValue,
                referenceValue,
                absoluteChange,
                changePercent,
                currency,
                detectedAt,
                message);
    }

    private static boolean sameSession(LocalDate current, LocalDate reference) {
        return current != null && current.equals(reference);
    }

    static BigDecimal percentChange(BigDecimal current, BigDecimal reference) {
        if (current == null || reference == null || reference.signum() == 0) {
            return null;
        }
        return current.subtract(reference)
                .multiply(BigDecimal.valueOf(100))
                .divide(reference, PERCENT_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal difference(BigDecimal current, BigDecimal reference) {
        return current.subtract(reference).setScale(PRICE_SCALE, RoundingMode.HALF_UP);
    }

    private static String format(BigDecimal value) {
        return value.abs().stripTrailingZeros().toPlainString();
    }

    private static BigDecimal requirePositive(BigDecimal value, String name) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalStateException(name + " must be greater than zero");
        }
        return value;
    }
}
