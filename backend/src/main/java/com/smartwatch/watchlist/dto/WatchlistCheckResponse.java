package com.smartwatch.watchlist.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * The cursor created when the user explicitly checks a watchlist.
 */
public record WatchlistCheckResponse(
        UUID watchlistId,
        UUID cursor,
        Instant checkedAt) {
}
