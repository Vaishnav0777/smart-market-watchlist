package com.smartwatch.watchlist.dto;

import java.util.List;

public record ChangeSummaryResponse(
        int totalChanges,
        int instrumentsWithChanges,
        int priceMoves,
        int volumeSpikes,
        int newDayHighs,
        int newDayLows,
        int gapUps,
        int gapDowns,
        int largeIntradayMoves,
        int watchlistAdded,
        int watchlistRemoved,
        List<String> highlights) {
}
