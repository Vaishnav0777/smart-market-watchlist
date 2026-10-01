package com.smartwatch.watchlist.controller;

import com.smartwatch.user.security.AuthenticatedUser;
import com.smartwatch.watchlist.dto.WatchlistCheckResponse;
import com.smartwatch.watchlist.dto.WatchlistChangesResponse;
import com.smartwatch.watchlist.service.WatchlistChangeService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/watchlists")
public class WatchlistChangeController {

    private final WatchlistChangeService changeService;
    private final AuthenticatedUser authenticatedUser;

    public WatchlistChangeController(WatchlistChangeService changeService, AuthenticatedUser authenticatedUser) {
        this.changeService = changeService;
        this.authenticatedUser = authenticatedUser;
    }

    @GetMapping("/{watchlistId}/changes")
    public ResponseEntity<WatchlistChangesResponse> changes(
            @PathVariable UUID watchlistId,
            @RequestParam(name = "since", required = false) String since) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(changeService.changes(authenticatedUser.requireId(), watchlistId, since));
    }

    @PostMapping("/{watchlistId}/checks")
    public ResponseEntity<WatchlistCheckResponse> acknowledge(@PathVariable UUID watchlistId) {
        WatchlistCheckResponse body = changeService.acknowledge(authenticatedUser.requireId(), watchlistId);
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(body);
    }
}
