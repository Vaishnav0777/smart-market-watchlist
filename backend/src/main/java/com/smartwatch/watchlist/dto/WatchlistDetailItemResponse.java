package com.smartwatch.watchlist.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * One watchlist row plus the latest observation, if the provider has one.
 *
 * <p>{@code quote} is null when the provider has no observation for this
 * instrument's exchange and symbol. A null quote is not stored.
 */
public record WatchlistDetailItemResponse(
        UUID id,
        int position,
        Instant addedAt,
        InstrumentResponse instrument,
        QuoteObservationResponse quote) {

    public static WatchlistDetailItemResponse of(WatchlistItemResponse item, QuoteObservationResponse quote) {
        return new WatchlistDetailItemResponse(
                item.id(),
                item.position(),
                item.addedAt(),
                item.instrument(),
                quote);
    }
}
