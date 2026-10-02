package com.smartwatch.portfolio.service;

import com.smartwatch.common.web.ApiException;
import com.smartwatch.marketdata.entity.Instrument;
import com.smartwatch.marketdata.model.Quote;
import com.smartwatch.marketdata.service.MarketDataService;
import com.smartwatch.portfolio.dto.PortfolioAnalyticsResponse;
import com.smartwatch.portfolio.entity.Portfolio;
import com.smartwatch.portfolio.entity.Position;
import com.smartwatch.portfolio.repository.PortfolioRepository;
import com.smartwatch.portfolio.service.PortfolioAnalytics.PositionInput;
import com.smartwatch.portfolio.service.PortfolioAnalytics.QuoteSnapshot;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Loads one owner's portfolio and the current quotes, then returns the
 * pure analytics result. It does not store that result.
 */
@Service
public class PortfolioAnalyticsService {

    private final PortfolioRepository portfolioRepository;
    private final MarketDataService marketDataService;

    public PortfolioAnalyticsService(PortfolioRepository portfolioRepository, MarketDataService marketDataService) {
        this.portfolioRepository = portfolioRepository;
        this.marketDataService = marketDataService;
    }

    @Transactional(readOnly = true)
    public PortfolioAnalyticsResponse analytics(UUID userId, UUID portfolioId) {
        Portfolio portfolio = portfolioRepository.findDetailedByIdAndUserId(portfolioId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, PortfolioQueryService.PORTFOLIO_NOT_FOUND));
        Map<String, Quote> quotes = quotesFor(portfolio);
        List<PositionInput> inputs = portfolio.getPositions().stream()
                .map(position -> toInput(position, quotes))
                .toList();
        return PortfolioAnalytics.calculate(portfolio.getId(), inputs);
    }

    private Map<String, Quote> quotesFor(Portfolio portfolio) {
        List<String> symbols = portfolio.getPositions().stream()
                .map(position -> position.getInstrument().getSymbol())
                .distinct()
                .toList();
        if (symbols.isEmpty()) {
            return Map.of();
        }
        Map<String, Quote> quotes = new HashMap<>();
        for (Quote quote : marketDataService.getQuotes(symbols)) {
            quotes.putIfAbsent(quoteKey(quote.exchange(), quote.symbol()), quote);
        }
        return quotes;
    }

    private static PositionInput toInput(Position position, Map<String, Quote> quotes) {
        Instrument instrument = position.getInstrument();
        Quote quote = quotes.get(quoteKey(instrument.getExchange(), instrument.getSymbol()));
        return new PositionInput(
                instrument.getId(),
                instrument.getSymbol(),
                instrument.getExchange(),
                instrument.getSector(),
                position.getQuantity(),
                position.getAverageBuyPrice(),
                snapshot(quote));
    }

    private static QuoteSnapshot snapshot(Quote quote) {
        if (quote == null || quote.price() == null) {
            return null;
        }
        return new QuoteSnapshot(
                quote.price(),
                quote.currency(),
                quote.quality(),
                quote.source(),
                quote.marketTimestamp(),
                quote.observedAt());
    }

    private static String quoteKey(String exchange, String symbol) {
        return exchange + "\n" + symbol;
    }
}
