package com.smartwatch.portfolio.service;

import com.smartwatch.marketdata.dto.InstrumentIdentityResponse;
import com.smartwatch.marketdata.dto.MarketQuoteResponse;
import com.smartwatch.marketdata.model.Quote;
import com.smartwatch.marketdata.service.MarketDataService;
import com.smartwatch.portfolio.dto.PortfolioResponse;
import com.smartwatch.portfolio.dto.PositionResponse;
import com.smartwatch.portfolio.entity.Portfolio;
import com.smartwatch.portfolio.entity.Position;
import com.smartwatch.portfolio.repository.PortfolioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Reads portfolios the caller already owns. It does not create holdings
 * and it does not calculate a return.
 */
@Service
public class PortfolioQueryService {

    private final PortfolioRepository portfolioRepository;
    private final MarketDataService marketDataService;

    public PortfolioQueryService(PortfolioRepository portfolioRepository, MarketDataService marketDataService) {
        this.portfolioRepository = portfolioRepository;
        this.marketDataService = marketDataService;
    }

    @Transactional(readOnly = true)
    public List<PortfolioResponse> list(UUID userId) {
        List<Portfolio> portfolios = portfolioRepository.findDetailedByUserId(userId);
        Map<String, Quote> quotes = quotesFor(portfolios);
        return portfolios.stream()
                .map(portfolio -> toResponse(portfolio, quotes))
                .toList();
    }

    private PortfolioResponse toResponse(Portfolio portfolio, Map<String, Quote> quotes) {
        List<PositionResponse> positions = portfolio.getPositions().stream()
                .sorted(Comparator.comparing(position -> position.getInstrument().getSymbol()))
                .map(position -> toPosition(position, quotes))
                .toList();
        return new PortfolioResponse(
                portfolio.getId(),
                portfolio.getName(),
                portfolio.getCreatedAt(),
                portfolio.getUpdatedAt(),
                positions);
    }

    private static PositionResponse toPosition(Position position, Map<String, Quote> quotes) {
        Quote quote = quotes.get(quoteKey(position.getInstrument().getExchange(), position.getInstrument().getSymbol()));
        return new PositionResponse(
                position.getId(),
                position.getQuantity(),
                position.getAverageBuyPrice(),
                InstrumentIdentityResponse.from(position.getInstrument()),
                quote == null ? null : MarketQuoteResponse.from(quote));
    }

    private Map<String, Quote> quotesFor(List<Portfolio> portfolios) {
        List<String> symbols = portfolios.stream()
                .flatMap(portfolio -> portfolio.getPositions().stream())
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

    private static String quoteKey(String exchange, String symbol) {
        return exchange + "\n" + symbol;
    }
}
