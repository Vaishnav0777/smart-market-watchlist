import { apiRequest } from "@/lib/api/client";
import type { Portfolio, Position } from "@/lib/types";

export function listPortfolios(): Promise<Portfolio[]> {
  return apiRequest<Portfolio[]>("/api/v1/portfolios");
}

export function getPortfolio(portfolioId: string): Promise<Portfolio> {
  return apiRequest<Portfolio>(`/api/v1/portfolios/${portfolioId}`);
}

export function createPortfolio(name: string): Promise<Portfolio> {
  return apiRequest<Portfolio>("/api/v1/portfolios", {
    method: "POST",
    body: { name },
  });
}

export function renamePortfolio(portfolioId: string, name: string): Promise<Portfolio> {
  return apiRequest<Portfolio>(`/api/v1/portfolios/${portfolioId}`, {
    method: "PATCH",
    body: { name },
  });
}

export function deletePortfolio(portfolioId: string): Promise<void> {
  return apiRequest<void>(`/api/v1/portfolios/${portfolioId}`, { method: "DELETE" });
}

export function addPosition(
  portfolioId: string,
  instrumentId: string,
  quantity: string,
  averageBuyPrice: string,
): Promise<Position> {
  return apiRequest<Position>(`/api/v1/portfolios/${portfolioId}/positions`, {
    method: "POST",
    body: { instrumentId, quantity, averageBuyPrice },
  });
}

export function updatePosition(
  portfolioId: string,
  positionId: string,
  quantity: string,
  averageBuyPrice: string,
): Promise<Position> {
  return apiRequest<Position>(`/api/v1/portfolios/${portfolioId}/positions/${positionId}`, {
    method: "PATCH",
    body: { quantity, averageBuyPrice },
  });
}

export function removePosition(portfolioId: string, positionId: string): Promise<void> {
  return apiRequest<void>(`/api/v1/portfolios/${portfolioId}/positions/${positionId}`, {
    method: "DELETE",
  });
}
