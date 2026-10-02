package com.smartwatch.assistant;

import com.smartwatch.portfolio.dto.PortfolioResponse;
import com.smartwatch.portfolio.service.PortfolioAnalyticsService;
import com.smartwatch.portfolio.service.PortfolioQueryService;
import com.smartwatch.watchlist.dto.WatchlistSummaryResponse;
import com.smartwatch.watchlist.service.WatchlistChangeService;
import com.smartwatch.watchlist.service.WatchlistService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Loads owner-scoped portfolio analytics and watchlist changes.
 * It does not acknowledge a check and does not call this application's HTTP API.
 */
@Service
public class AssistantContextBuilder {

    private final PortfolioQueryService portfolioQueryService;
    private final PortfolioAnalyticsService portfolioAnalyticsService;
    private final WatchlistService watchlistService;
    private final WatchlistChangeService watchlistChangeService;

    public AssistantContextBuilder(
            PortfolioQueryService portfolioQueryService,
            PortfolioAnalyticsService portfolioAnalyticsService,
            WatchlistService watchlistService,
            WatchlistChangeService watchlistChangeService) {
        this.portfolioQueryService = portfolioQueryService;
        this.portfolioAnalyticsService = portfolioAnalyticsService;
        this.watchlistService = watchlistService;
        this.watchlistChangeService = watchlistChangeService;
    }

    public AssistantContext build(UUID userId, UUID portfolioId) {
        return new AssistantContext(portfolios(userId, portfolioId), watchlists(userId));
    }

    private List<AssistantContext.PortfolioSnapshot> portfolios(UUID userId, UUID portfolioId) {
        if (portfolioId != null) {
            PortfolioResponse portfolio = portfolioQueryService.get(userId, portfolioId);
            return List.of(new AssistantContext.PortfolioSnapshot(
                    portfolio.id(),
                    portfolio.name(),
                    portfolioAnalyticsService.analytics(userId, portfolio.id())));
        }
        return portfolioQueryService.list(userId).stream()
                .map(portfolio -> new AssistantContext.PortfolioSnapshot(
                        portfolio.id(),
                        portfolio.name(),
                        portfolioAnalyticsService.analytics(userId, portfolio.id())))
                .toList();
    }

    private List<AssistantContext.WatchlistSnapshot> watchlists(UUID userId) {
        List<WatchlistSummaryResponse> watchlists = watchlistService.list(userId);
        return watchlists.stream()
                .map(watchlist -> new AssistantContext.WatchlistSnapshot(
                        watchlist.id(),
                        watchlist.name(),
                        watchlistChangeService.changes(userId, watchlist.id(), null)))
                .toList();
    }
}
