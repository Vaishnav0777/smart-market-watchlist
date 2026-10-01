import type { ChangeSeverity, ChangeType } from "@/lib/types";

const inr = new Intl.NumberFormat("en-IN", {
  style: "currency",
  currency: "INR",
  maximumFractionDigits: 2,
});

const quantity = new Intl.NumberFormat("en-IN", {
  maximumFractionDigits: 4,
});

const volume = new Intl.NumberFormat("en-IN");

export function formatMoney(value: number | null | undefined, currency: string | null | undefined): string {
  if (value == null || Number.isNaN(value)) {
    return "—";
  }
  if (currency === "INR") {
    return inr.format(value);
  }
  if (!currency) {
    return quantity.format(value);
  }
  return `${quantity.format(value)} ${currency}`;
}

export function formatQuantity(value: number | null | undefined): string {
  if (value == null || Number.isNaN(value)) {
    return "—";
  }
  return quantity.format(value);
}

export function formatVolume(value: number | null | undefined): string {
  if (value == null || Number.isNaN(value)) {
    return "—";
  }
  return volume.format(value);
}

export function formatPercent(value: number | null | undefined): string | null {
  if (value == null || Number.isNaN(value)) {
    return null;
  }
  const sign = value > 0 ? "+" : "";
  return `${sign}${trimNumber(value)}%`;
}

export function previousCloseMove(price: number, previousClose: number | null): number | null {
  if (previousClose == null || previousClose === 0) {
    return null;
  }
  return ((price - previousClose) / previousClose) * 100;
}

export function formatTimestamp(value: string | null | undefined): string {
  if (!value) {
    return "—";
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return new Intl.DateTimeFormat("en-IN", {
    dateStyle: "medium",
    timeStyle: "short",
    timeZone: "Asia/Kolkata",
  }).format(date);
}

export const changeTypeLabel: Record<ChangeType, string> = {
  PRICE_MOVE: "Price movement",
  VOLUME_SPIKE: "Volume spike",
  NEW_DAY_HIGH: "New day high",
  NEW_DAY_LOW: "New day low",
  GAP_UP: "Gap up",
  GAP_DOWN: "Gap down",
  LARGE_INTRADAY_MOVE: "Large intraday move",
  WATCHLIST_ADDED: "Added to watchlist",
  WATCHLIST_REMOVED: "Removed from watchlist",
};

export function severityLabel(severity: ChangeSeverity): string {
  return severity === "HIGH" ? "High relevance" : "Notable";
}

function trimNumber(value: number): string {
  const fixed = value.toFixed(2);
  return fixed.replace(/\.00$/, "").replace(/(\.\d)0$/, "$1");
}
