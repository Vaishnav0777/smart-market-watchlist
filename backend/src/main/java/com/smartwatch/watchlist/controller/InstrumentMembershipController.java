package com.smartwatch.watchlist.controller;

import com.smartwatch.user.security.AuthenticatedUser;
import com.smartwatch.watchlist.dto.InstrumentMembershipResponse;
import com.smartwatch.watchlist.service.WatchlistService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/instruments")
public class InstrumentMembershipController {

    private final WatchlistService watchlistService;
    private final AuthenticatedUser authenticatedUser;

    public InstrumentMembershipController(WatchlistService watchlistService, AuthenticatedUser authenticatedUser) {
        this.watchlistService = watchlistService;
        this.authenticatedUser = authenticatedUser;
    }

    @GetMapping("/{instrumentId}/memberships")
    public ResponseEntity<List<InstrumentMembershipResponse>> memberships(@PathVariable UUID instrumentId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(watchlistService.memberships(authenticatedUser.requireId(), instrumentId));
    }
}
