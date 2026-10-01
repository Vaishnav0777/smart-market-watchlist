package com.smartwatch.portfolio.controller;

import com.smartwatch.portfolio.dto.PortfolioResponse;
import com.smartwatch.portfolio.service.PortfolioQueryService;
import com.smartwatch.user.security.AuthenticatedUser;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/portfolios")
public class PortfolioController {

    private final PortfolioQueryService portfolioQueryService;
    private final AuthenticatedUser authenticatedUser;

    public PortfolioController(PortfolioQueryService portfolioQueryService, AuthenticatedUser authenticatedUser) {
        this.portfolioQueryService = portfolioQueryService;
        this.authenticatedUser = authenticatedUser;
    }

    @GetMapping
    public ResponseEntity<List<PortfolioResponse>> list() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(portfolioQueryService.list(authenticatedUser.requireId()));
    }
}
