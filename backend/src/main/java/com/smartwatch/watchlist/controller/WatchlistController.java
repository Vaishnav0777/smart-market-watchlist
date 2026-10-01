package com.smartwatch.watchlist.controller;

import com.smartwatch.user.security.AuthenticatedUser;
import com.smartwatch.watchlist.dto.AddWatchlistItemRequest;
import com.smartwatch.watchlist.dto.CreateWatchlistRequest;
import com.smartwatch.watchlist.dto.ReorderWatchlistItemsRequest;
import com.smartwatch.watchlist.dto.UpdateWatchlistRequest;
import com.smartwatch.watchlist.dto.WatchlistDetailResponse;
import com.smartwatch.watchlist.dto.WatchlistItemResponse;
import com.smartwatch.watchlist.dto.WatchlistSummaryResponse;
import com.smartwatch.watchlist.service.WatchlistService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/watchlists")
public class WatchlistController {

    private final WatchlistService watchlistService;
    private final AuthenticatedUser authenticatedUser;

    public WatchlistController(WatchlistService watchlistService, AuthenticatedUser authenticatedUser) {
        this.watchlistService = watchlistService;
        this.authenticatedUser = authenticatedUser;
    }

    @PostMapping
    public ResponseEntity<WatchlistSummaryResponse> create(@Valid @RequestBody CreateWatchlistRequest request) {
        WatchlistSummaryResponse body = watchlistService.create(authenticatedUser.requireId(), request.name());
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(body);
    }

    @GetMapping
    public ResponseEntity<List<WatchlistSummaryResponse>> list() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(watchlistService.list(authenticatedUser.requireId()));
    }

    @GetMapping("/{watchlistId}")
    public ResponseEntity<WatchlistDetailResponse> get(@PathVariable UUID watchlistId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(watchlistService.get(authenticatedUser.requireId(), watchlistId));
    }

    @PatchMapping("/{watchlistId}")
    public ResponseEntity<WatchlistSummaryResponse> rename(
            @PathVariable UUID watchlistId,
            @Valid @RequestBody UpdateWatchlistRequest request) {
        WatchlistSummaryResponse body = watchlistService.rename(
                authenticatedUser.requireId(),
                watchlistId,
                request.name());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }

    @DeleteMapping("/{watchlistId}")
    public ResponseEntity<Void> delete(@PathVariable UUID watchlistId) {
        watchlistService.delete(authenticatedUser.requireId(), watchlistId);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @PostMapping("/{watchlistId}/items")
    public ResponseEntity<WatchlistItemResponse> addItem(
            @PathVariable UUID watchlistId,
            @Valid @RequestBody AddWatchlistItemRequest request) {
        WatchlistItemResponse body = watchlistService.addItem(
                authenticatedUser.requireId(),
                watchlistId,
                request.instrumentId());
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(body);
    }

    @DeleteMapping("/{watchlistId}/items/{itemId}")
    public ResponseEntity<Void> removeItem(@PathVariable UUID watchlistId, @PathVariable UUID itemId) {
        watchlistService.removeItem(authenticatedUser.requireId(), watchlistId, itemId);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @PutMapping("/{watchlistId}/items/order")
    public ResponseEntity<WatchlistDetailResponse> reorder(
            @PathVariable UUID watchlistId,
            @Valid @RequestBody ReorderWatchlistItemsRequest request) {
        UUID userId = authenticatedUser.requireId();
        watchlistService.reorder(userId, watchlistId, request.itemIds());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(watchlistService.get(userId, watchlistId));
    }
}
