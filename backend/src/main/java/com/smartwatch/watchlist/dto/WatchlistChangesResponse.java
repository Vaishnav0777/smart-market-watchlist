package com.smartwatch.watchlist.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Changes relative to a saved check. {@code firstCheck} is true when no
 * checkpoint exists yet. This response does not itself record a check.
 */
public record WatchlistChangesResponse(
        UUID watchlistId,
        UUID cursor,
        Instant baselineCheckedAt,
        boolean firstCheck,
        List<ChangeResponse> changes,
        ChangeSummaryResponse summary) {
}
