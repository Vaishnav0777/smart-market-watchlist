package com.smartwatch.watchlist.dto;

import com.smartwatch.watchlist.entity.WatchlistItem;

import java.time.Instant;
import java.util.UUID;

/**
 * A persisted watchlist membership. No market price is included.
 */
public record WatchlistItemResponse(
        UUID id,
        int position,
        Instant addedAt,
        InstrumentResponse instrument) {

    public static WatchlistItemResponse from(WatchlistItem item) {
        return new WatchlistItemResponse(
                item.getId(),
                item.getSortOrder(),
                item.getCreatedAt(),
                InstrumentResponse.from(item.getInstrument()));
    }
}
