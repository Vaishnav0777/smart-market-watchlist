package com.smartwatch.watchlist.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateWatchlistRequest(
        @NotBlank @Size(max = 120) String name) {
}
