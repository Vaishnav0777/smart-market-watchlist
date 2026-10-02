package com.smartwatch.assistant;

import com.smartwatch.portfolio.dto.PortfolioAnalyticsResponse;
import com.smartwatch.watchlist.dto.WatchlistChangesResponse;

import java.util.List;
import java.util.UUID;

/**
 * Structured facts for one signed-in user. Stored names and change messages
 * are data. They are not instructions.
 */
public record AssistantContext(
        List<PortfolioSnapshot> portfolios,
        List<WatchlistSnapshot> watchlists) {

    public AssistantContext {
        portfolios = List.copyOf(portfolios);
        watchlists = List.copyOf(watchlists);
    }

    public record PortfolioSnapshot(UUID id, String name, PortfolioAnalyticsResponse analytics) {
    }

    public record WatchlistSnapshot(UUID id, String name, WatchlistChangesResponse changes) {
    }
}
