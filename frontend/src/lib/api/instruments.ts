import { apiRequest } from "@/lib/api/client";
import type { InstrumentDetail, InstrumentListing, InstrumentMembership } from "@/lib/types";

export function searchInstruments(query: string): Promise<InstrumentListing[]> {
  const params = new URLSearchParams();
  if (query.trim()) {
    params.set("q", query.trim());
  }
  const suffix = params.size > 0 ? `?${params.toString()}` : "";
  return apiRequest<InstrumentListing[]>(`/api/v1/instruments${suffix}`);
}

export function getInstrument(instrumentId: string): Promise<InstrumentDetail> {
  return apiRequest<InstrumentDetail>(`/api/v1/instruments/${instrumentId}`);
}

export function getInstrumentMemberships(instrumentId: string): Promise<InstrumentMembership[]> {
  return apiRequest<InstrumentMembership[]>(`/api/v1/instruments/${instrumentId}/memberships`);
}
