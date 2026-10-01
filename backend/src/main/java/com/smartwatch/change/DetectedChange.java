package com.smartwatch.change;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One observable change. {@code absoluteChange} is the signed difference
 * in the same units as the values. Negative means a decrease.
 */
public record DetectedChange(
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
}
