package com.smartwatch.portfolio.controller;

import com.smartwatch.portfolio.dto.PortfolioNameRequest;
import com.smartwatch.portfolio.dto.PortfolioResponse;
import com.smartwatch.portfolio.dto.PositionRequest;
import com.smartwatch.portfolio.dto.PositionResponse;
import com.smartwatch.portfolio.dto.UpdatePositionRequest;
import com.smartwatch.portfolio.service.PortfolioQueryService;
import com.smartwatch.user.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/portfolios")
public class PortfolioController {

    private final PortfolioQueryService portfolioService;
    private final AuthenticatedUser authenticatedUser;

    public PortfolioController(PortfolioQueryService portfolioService, AuthenticatedUser authenticatedUser) {
        this.portfolioService = portfolioService;
        this.authenticatedUser = authenticatedUser;
    }

    @GetMapping
    public ResponseEntity<List<PortfolioResponse>> list() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(portfolioService.list(authenticatedUser.requireId()));
    }

    @PostMapping
    public ResponseEntity<PortfolioResponse> create(@Valid @RequestBody PortfolioNameRequest request) {
        PortfolioResponse body = portfolioService.create(authenticatedUser.requireId(), request.name());
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(body);
    }

    @GetMapping("/{portfolioId}")
    public ResponseEntity<PortfolioResponse> get(@PathVariable UUID portfolioId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(portfolioService.get(authenticatedUser.requireId(), portfolioId));
    }

    @PatchMapping("/{portfolioId}")
    public ResponseEntity<PortfolioResponse> rename(
            @PathVariable UUID portfolioId,
            @Valid @RequestBody PortfolioNameRequest request) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(portfolioService.rename(authenticatedUser.requireId(), portfolioId, request.name()));
    }

    @DeleteMapping("/{portfolioId}")
    public ResponseEntity<Void> delete(@PathVariable UUID portfolioId) {
        portfolioService.delete(authenticatedUser.requireId(), portfolioId);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @GetMapping("/{portfolioId}/positions")
    public ResponseEntity<List<PositionResponse>> positions(@PathVariable UUID portfolioId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(portfolioService.get(authenticatedUser.requireId(), portfolioId).positions());
    }

    @PostMapping("/{portfolioId}/positions")
    public ResponseEntity<PositionResponse> addPosition(
            @PathVariable UUID portfolioId,
            @Valid @RequestBody PositionRequest request) {
        PositionResponse body = portfolioService.addPosition(
                authenticatedUser.requireId(),
                portfolioId,
                request.instrumentId(),
                request.quantity(),
                request.averageBuyPrice());
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(body);
    }

    @PatchMapping("/{portfolioId}/positions/{positionId}")
    public ResponseEntity<PositionResponse> updatePosition(
            @PathVariable UUID portfolioId,
            @PathVariable UUID positionId,
            @Valid @RequestBody UpdatePositionRequest request) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(portfolioService.updatePosition(
                        authenticatedUser.requireId(),
                        portfolioId,
                        positionId,
                        request.quantity(),
                        request.averageBuyPrice()));
    }

    @DeleteMapping("/{portfolioId}/positions/{positionId}")
    public ResponseEntity<Void> removePosition(@PathVariable UUID portfolioId, @PathVariable UUID positionId) {
        portfolioService.removePosition(authenticatedUser.requireId(), portfolioId, positionId);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
