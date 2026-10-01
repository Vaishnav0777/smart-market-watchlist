package com.smartwatch.watchlist.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Changes relative to a cursor. {@code baselineCheckedAt} is the cursor
 * time. This response does not mean the watchlist was just checked.
 */
public record WatchlistChangesResponse(
        UUID watchlistId,
        UUID cursor,
        Instant baselineCheckedAt,
        List<ChangeResponse> changes,
        ChangeSummaryResponse summary) {
}
