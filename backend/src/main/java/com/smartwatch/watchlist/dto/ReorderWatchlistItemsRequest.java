package com.smartwatch.watchlist.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record ReorderWatchlistItemsRequest(
        @NotNull List<@NotNull UUID> itemIds) {
}
