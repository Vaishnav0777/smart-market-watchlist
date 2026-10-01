package com.smartwatch.watchlist.dto;

import com.smartwatch.change.ChangeSeverity;
import com.smartwatch.change.ChangeType;
import com.smartwatch.change.DetectedChange;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ChangeResponse(
        UUID instrumentId,
        String symbol,
        String exchange,
        ChangeType type,
        ChangeSeverity severity,
        BigDecimal currentValue,
        BigDecimal referenceValue,
        BigDecimal absoluteChange,
        BigDecimal changePercent,
        String currency,
        Instant detectedAt,
        String message) {

    public static ChangeResponse from(DetectedChange change) {
        return new ChangeResponse(
                change.instrumentId(),
                change.symbol(),
                change.exchange(),
                change.type(),
                change.severity(),
                change.currentValue(),
                change.referenceValue(),
                change.absoluteChange(),
                change.changePercent(),
                change.currency(),
                change.detectedAt(),
                change.message());
    }
}
