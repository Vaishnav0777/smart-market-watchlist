package com.smartwatch.watchlist.dto;

import com.smartwatch.watchlist.entity.Watchlist;

import java.time.Instant;
import java.util.UUID;

public record WatchlistSummaryResponse(
        UUID id,
        String name,
        long itemCount,
        Instant createdAt,
        Instant updatedAt) {

    public static WatchlistSummaryResponse from(Watchlist watchlist, long itemCount) {
        return new WatchlistSummaryResponse(
                watchlist.getId(),
                watchlist.getName(),
                itemCount,
                watchlist.getCreatedAt(),
                watchlist.getUpdatedAt());
    }
}
