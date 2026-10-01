import { apiRequest } from "@/lib/api/client";
import type { WatchlistChanges, WatchlistCheck } from "@/lib/types";

export function getChanges(watchlistId: string, since?: string): Promise<WatchlistChanges> {
  const params = new URLSearchParams();
  if (since) {
    params.set("since", since);
  }
  const suffix = params.size > 0 ? `?${params.toString()}` : "";
  return apiRequest<WatchlistChanges>(`/api/v1/watchlists/${watchlistId}/changes${suffix}`);
}

export function acknowledgeCheck(watchlistId: string): Promise<WatchlistCheck> {
  return apiRequest<WatchlistCheck>(`/api/v1/watchlists/${watchlistId}/checks`, { method: "POST" });
}
