package com.smartwatch.watchlist.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AddWatchlistItemRequest(
        @NotNull UUID instrumentId) {
}
