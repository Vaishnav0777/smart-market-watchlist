import { apiRequest } from "@/lib/api/client";
import type { WatchlistDetail, WatchlistItem, WatchlistSummary } from "@/lib/types";

export function listWatchlists(): Promise<WatchlistSummary[]> {
  return apiRequest<WatchlistSummary[]>("/api/v1/watchlists");
}

export function getWatchlist(watchlistId: string): Promise<WatchlistDetail> {
  return apiRequest<WatchlistDetail>(`/api/v1/watchlists/${watchlistId}`);
}

export function createWatchlist(name: string): Promise<WatchlistSummary> {
  return apiRequest<WatchlistSummary>("/api/v1/watchlists", {
    method: "POST",
    body: { name },
  });
}

export function renameWatchlist(watchlistId: string, name: string): Promise<WatchlistSummary> {
  return apiRequest<WatchlistSummary>(`/api/v1/watchlists/${watchlistId}`, {
    method: "PATCH",
    body: { name },
  });
}

export function deleteWatchlist(watchlistId: string): Promise<void> {
  return apiRequest<void>(`/api/v1/watchlists/${watchlistId}`, { method: "DELETE" });
}

export function addWatchlistItem(watchlistId: string, instrumentId: string): Promise<WatchlistItem> {
  return apiRequest<WatchlistItem>(`/api/v1/watchlists/${watchlistId}/items`, {
    method: "POST",
    body: { instrumentId },
  });
}

export function removeWatchlistItem(watchlistId: string, itemId: string): Promise<void> {
  return apiRequest<void>(`/api/v1/watchlists/${watchlistId}/items/${itemId}`, { method: "DELETE" });
}

export function reorderWatchlistItems(watchlistId: string, itemIds: string[]): Promise<WatchlistDetail> {
  return apiRequest<WatchlistDetail>(`/api/v1/watchlists/${watchlistId}/items/order`, {
    method: "PUT",
    body: { itemIds },
  });
}
