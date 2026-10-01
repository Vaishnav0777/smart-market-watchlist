package com.smartwatch.marketdata.controller;

import com.smartwatch.marketdata.dto.InstrumentDetailResponse;
import com.smartwatch.marketdata.dto.InstrumentListingResponse;
import com.smartwatch.marketdata.dto.MarketQuoteResponse;
import com.smartwatch.marketdata.service.InstrumentDirectoryService;
import com.smartwatch.marketdata.service.MarketDataService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class InstrumentController {

    private final InstrumentDirectoryService directoryService;
    private final MarketDataService marketDataService;

    public InstrumentController(InstrumentDirectoryService directoryService, MarketDataService marketDataService) {
        this.directoryService = directoryService;
        this.marketDataService = marketDataService;
    }

    @GetMapping("/instruments")
    public ResponseEntity<List<InstrumentListingResponse>> search(
            @RequestParam(name = "q", required = false) String query) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(directoryService.search(query));
    }

    @GetMapping("/instruments/{instrumentId}")
    public ResponseEntity<InstrumentDetailResponse> get(@PathVariable UUID instrumentId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(directoryService.get(instrumentId));
    }

    @GetMapping("/market/quotes")
    public ResponseEntity<List<MarketQuoteResponse>> quotes() {
        List<MarketQuoteResponse> body = marketDataService.listQuotes().stream()
                .map(MarketQuoteResponse::from)
                .toList();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
