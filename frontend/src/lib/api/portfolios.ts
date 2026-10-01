import { apiRequest } from "@/lib/api/client";
import type { Portfolio } from "@/lib/types";

export function listPortfolios(): Promise<Portfolio[]> {
  return apiRequest<Portfolio[]>("/api/v1/portfolios");
}
