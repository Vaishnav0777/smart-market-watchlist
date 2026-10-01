package com.smartwatch.watchlist.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateWatchlistRequest(
        @NotBlank @Size(max = 120) String name) {
}
