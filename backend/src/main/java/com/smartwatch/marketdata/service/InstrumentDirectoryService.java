package com.smartwatch.marketdata.service;

import com.smartwatch.common.web.ApiException;
import com.smartwatch.marketdata.dto.InstrumentDetailResponse;
import com.smartwatch.marketdata.dto.InstrumentIdentityResponse;
import com.smartwatch.marketdata.dto.InstrumentListingResponse;
import com.smartwatch.marketdata.dto.MarketQuoteResponse;
import com.smartwatch.marketdata.entity.Instrument;
import com.smartwatch.marketdata.entity.InstrumentType;
import com.smartwatch.marketdata.model.Quote;
import com.smartwatch.marketdata.repository.InstrumentRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Read-only discovery of instruments the market-data provider can identify.
 *
 * <p>Search materializes a persisted identity for a provider listing when one
 * does not exist yet. It does not accept an arbitrary symbol from the client,
 * and it does not store the quote.
 */
@Service
public class InstrumentDirectoryService {

    static final String INSTRUMENT_NOT_FOUND = "Instrument not found";
    static final String QUERY_TOO_LONG = "Search text must be 64 characters or fewer";

    private final InstrumentRepository instrumentRepository;
    private final MarketDataService marketDataService;

    public InstrumentDirectoryService(
            InstrumentRepository instrumentRepository,
            MarketDataService marketDataService) {
        this.instrumentRepository = instrumentRepository;
        this.marketDataService = marketDataService;
    }

    @Transactional
    public List<InstrumentListingResponse> search(String query) {
        String normalized = query == null ? "" : query.trim();
        if (normalized.length() > 64) {
            throw new ApiException(HttpStatus.BAD_REQUEST, QUERY_TOO_LONG);
        }
        String needle = normalized.toLowerCase(Locale.ROOT);
        List<InstrumentListingResponse> listings = new ArrayList<>();
        for (Quote quote : marketDataService.listQuotes()) {
            if (!needle.isEmpty() && !matches(quote, needle)) {
                continue;
            }
            Instrument instrument = resolveIdentity(quote);
            listings.add(new InstrumentListingResponse(
                    InstrumentIdentityResponse.from(instrument),
                    MarketQuoteResponse.from(quote)));
        }
        return List.copyOf(listings);
    }

    @Transactional(readOnly = true)
    public InstrumentDetailResponse get(UUID instrumentId) {
        Instrument instrument = instrumentRepository.findById(instrumentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, INSTRUMENT_NOT_FOUND));
        MarketQuoteResponse quote = marketDataService.listQuotes().stream()
                .filter(candidate -> sameListing(candidate, instrument))
                .findFirst()
                .map(MarketQuoteResponse::from)
                .orElse(null);
        return new InstrumentDetailResponse(InstrumentIdentityResponse.from(instrument), quote);
    }

    private Instrument resolveIdentity(Quote quote) {
        return instrumentRepository.findByExchangeAndSymbol(quote.exchange(), quote.symbol())
                .orElseGet(() -> saveIdentity(quote));
    }

    private Instrument saveIdentity(Quote quote) {
        try {
            return instrumentRepository.saveAndFlush(new Instrument(
                    quote.symbol(),
                    quote.companyName(),
                    quote.exchange(),
                    quote.sector(),
                    InstrumentType.EQUITY));
        } catch (DataIntegrityViolationException exception) {
            return instrumentRepository.findByExchangeAndSymbol(quote.exchange(), quote.symbol())
                    .orElseThrow(() -> exception);
        }
    }

    private static boolean matches(Quote quote, String needle) {
        return quote.symbol().toLowerCase(Locale.ROOT).contains(needle)
                || quote.companyName().toLowerCase(Locale.ROOT).contains(needle);
    }

    private static boolean sameListing(Quote quote, Instrument instrument) {
        return quote.exchange().equals(instrument.getExchange())
                && quote.symbol().equals(instrument.getSymbol());
    }
}
