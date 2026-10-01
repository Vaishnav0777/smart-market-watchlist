package com.smartwatch.change;

import java.util.UUID;

/**
 * One instrument to evaluate. {@code reference} is null when no earlier
 * observation is available. A missing reference is not treated as zero.
 */
public record ChangeSubject(
        UUID instrumentId,
        String symbol,
        String exchange,
        MembershipDelta membership,
        MarketSnapshot current,
        MarketSnapshot reference) {
}
