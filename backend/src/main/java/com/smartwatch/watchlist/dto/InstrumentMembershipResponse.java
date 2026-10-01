package com.smartwatch.watchlist.dto;

import java.util.UUID;

public record InstrumentMembershipResponse(
        UUID watchlistId,
        String watchlistName,
        UUID itemId) {
}
