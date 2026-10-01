package com.smartwatch.portfolio.service;

import com.smartwatch.common.web.ApiException;
import com.smartwatch.marketdata.dto.InstrumentIdentityResponse;
import com.smartwatch.marketdata.dto.MarketQuoteResponse;
import com.smartwatch.marketdata.entity.Instrument;
import com.smartwatch.marketdata.model.Quote;
import com.smartwatch.marketdata.repository.InstrumentRepository;
import com.smartwatch.marketdata.service.MarketDataService;
import com.smartwatch.portfolio.dto.PortfolioResponse;
import com.smartwatch.portfolio.dto.PositionResponse;
import com.smartwatch.portfolio.entity.Portfolio;
import com.smartwatch.portfolio.entity.Position;
import com.smartwatch.portfolio.repository.PortfolioRepository;
import com.smartwatch.portfolio.repository.PositionRepository;
import com.smartwatch.user.entity.User;
import com.smartwatch.user.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Portfolios and positions for the authenticated owner.
 * A quote is attached when the provider has one. This service does not
 * calculate a return and does not create instruments.
 */
@Service
public class PortfolioQueryService {

    static final String PORTFOLIO_NOT_FOUND = "Portfolio not found";
    static final String POSITION_NOT_FOUND = "Position not found";
    static final String INSTRUMENT_NOT_FOUND = "Instrument not found";
    static final String DUPLICATE_NAME = "A portfolio with this name already exists";
    static final String DUPLICATE_POSITION = "This instrument is already in the portfolio";
    static final String INVALID_NAME = "Portfolio name must be between 1 and 120 characters";
    static final String INVALID_QUANTITY = "Quantity must be greater than zero and use at most 4 decimal places";
    static final String INVALID_PRICE = "Average price must be greater than zero and use at most 4 decimal places";

    private final PortfolioRepository portfolioRepository;
    private final PositionRepository positionRepository;
    private final InstrumentRepository instrumentRepository;
    private final UserRepository userRepository;
    private final MarketDataService marketDataService;

    public PortfolioQueryService(
            PortfolioRepository portfolioRepository,
            PositionRepository positionRepository,
            InstrumentRepository instrumentRepository,
            UserRepository userRepository,
            MarketDataService marketDataService) {
        this.portfolioRepository = portfolioRepository;
        this.positionRepository = positionRepository;
        this.instrumentRepository = instrumentRepository;
        this.userRepository = userRepository;
        this.marketDataService = marketDataService;
    }

    @Transactional
    public PortfolioResponse create(UUID userId, String name) {
        String normalized = normalizeName(name);
        if (portfolioRepository.existsByUser_IdAndName(userId, normalized)) {
            throw new ApiException(HttpStatus.CONFLICT, DUPLICATE_NAME);
        }
        User owner = userRepository.getReferenceById(userId);
        try {
            Portfolio portfolio = portfolioRepository.saveAndFlush(new Portfolio(owner, normalized));
            return toResponse(portfolio, Map.of());
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(HttpStatus.CONFLICT, DUPLICATE_NAME);
        }
    }

    @Transactional(readOnly = true)
    public PortfolioResponse get(UUID userId, UUID portfolioId) {
        Portfolio portfolio = requireDetailed(userId, portfolioId);
        return toResponse(portfolio, quotesFor(List.of(portfolio)));
    }

    @Transactional
    public PortfolioResponse rename(UUID userId, UUID portfolioId, String name) {
        Portfolio portfolio = requireOwned(userId, portfolioId);
        String normalized = normalizeName(name);
        if (!portfolio.getName().equals(normalized) && portfolioRepository.existsByUser_IdAndName(userId, normalized)) {
            throw new ApiException(HttpStatus.CONFLICT, DUPLICATE_NAME);
        }
        portfolio.renameTo(normalized);
        try {
            portfolioRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(HttpStatus.CONFLICT, DUPLICATE_NAME);
        }
        return get(userId, portfolioId);
    }

    @Transactional
    public void delete(UUID userId, UUID portfolioId) {
        Portfolio portfolio = requireOwned(userId, portfolioId);
        UUID id = portfolio.getId();
        positionRepository.deleteByPortfolioId(id);
        portfolioRepository.deleteById(id);
    }

    @Transactional
    public PositionResponse addPosition(UUID userId, UUID portfolioId, UUID instrumentId, BigDecimal quantity, BigDecimal price) {
        Portfolio portfolio = requireOwned(userId, portfolioId);
        BigDecimal storedQuantity = positiveAmount(quantity, INVALID_QUANTITY);
        BigDecimal storedPrice = positiveAmount(price, INVALID_PRICE);
        Instrument instrument = instrumentRepository.findById(instrumentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, INSTRUMENT_NOT_FOUND));
        if (positionRepository.existsByPortfolio_IdAndInstrument_Id(portfolioId, instrumentId)) {
            throw new ApiException(HttpStatus.CONFLICT, DUPLICATE_POSITION);
        }
        try {
            Position position = positionRepository.saveAndFlush(
                    new Position(portfolio, instrument, storedQuantity, storedPrice));
            return toPosition(position, quotesForInstrument(instrument));
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(HttpStatus.CONFLICT, DUPLICATE_POSITION);
        }
    }

    @Transactional
    public PositionResponse updatePosition(
            UUID userId,
            UUID portfolioId,
            UUID positionId,
            BigDecimal quantity,
            BigDecimal price) {
        requireOwned(userId, portfolioId);
        Position position = positionRepository.findByIdAndPortfolio_Id(positionId, portfolioId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, POSITION_NOT_FOUND));
        position.changeHolding(positiveAmount(quantity, INVALID_QUANTITY), positiveAmount(price, INVALID_PRICE));
        positionRepository.flush();
        return toPosition(position, quotesForInstrument(position.getInstrument()));
    }

    @Transactional
    public void removePosition(UUID userId, UUID portfolioId, UUID positionId) {
        requireOwned(userId, portfolioId);
        Position position = positionRepository.findByIdAndPortfolio_Id(positionId, portfolioId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, POSITION_NOT_FOUND));
        positionRepository.delete(position);
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

    private Map<String, Quote> quotesForInstrument(Instrument instrument) {
        Map<String, Quote> quotes = new HashMap<>();
        for (Quote quote : marketDataService.getQuotes(List.of(instrument.getSymbol()))) {
            quotes.putIfAbsent(quoteKey(quote.exchange(), quote.symbol()), quote);
        }
        return quotes;
    }

    private Portfolio requireOwned(UUID userId, UUID portfolioId) {
        return portfolioRepository.findByIdAndUser_Id(portfolioId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, PORTFOLIO_NOT_FOUND));
    }

    private Portfolio requireDetailed(UUID userId, UUID portfolioId) {
        return portfolioRepository.findDetailedByIdAndUserId(portfolioId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, PORTFOLIO_NOT_FOUND));
    }

    private static String normalizeName(String name) {
        if (name == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, INVALID_NAME);
        }
        String trimmed = name.trim();
        if (trimmed.isEmpty() || trimmed.length() > 120) {
            throw new ApiException(HttpStatus.BAD_REQUEST, INVALID_NAME);
        }
        return trimmed;
    }

    private static BigDecimal positiveAmount(BigDecimal value, String message) {
        if (value == null || value.signum() <= 0 || value.scale() > 4 || value.precision() > 19) {
            throw new ApiException(HttpStatus.BAD_REQUEST, message);
        }
        try {
            return value.setScale(4, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, message);
        }
    }

    private static String quoteKey(String exchange, String symbol) {
        return exchange + "\n" + symbol;
    }
}
