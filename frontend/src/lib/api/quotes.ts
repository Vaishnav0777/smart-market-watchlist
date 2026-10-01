import { apiRequest } from "@/lib/api/client";
import type { MarketQuote } from "@/lib/types";

export function listQuotes(): Promise<MarketQuote[]> {
  return apiRequest<MarketQuote[]>("/api/v1/market/quotes");
}
