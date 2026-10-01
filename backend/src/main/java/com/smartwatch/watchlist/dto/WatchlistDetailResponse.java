package com.smartwatch.watchlist.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record WatchlistDetailResponse(
        UUID id,
        String name,
        Instant createdAt,
        Instant updatedAt,
        List<WatchlistDetailItemResponse> items) {
}
