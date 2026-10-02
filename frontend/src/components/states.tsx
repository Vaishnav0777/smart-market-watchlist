import { ApiError } from "@/lib/api/client";
import { qualityLabel } from "@/lib/format";
import type { MarketDataQuality, MarketDataSource } from "@/lib/types";
import type { ReactNode } from "react";

export function LoadingState({ label = "Loading" }: { label?: string }) {
  return (
    <div className="flex items-center gap-2 px-1 py-3 text-sm text-muted" role="status">
      <span className="h-1.5 w-1.5 animate-pulse rounded-full bg-muted" />
      {label}
    </div>
  );
}

export function EmptyState({ title, body, children }: { title: string; body: string; children?: ReactNode }) {
  return (
    <div className="surface px-4 py-4">
      <h2 className="text-sm font-semibold text-foreground">{title}</h2>
      <p className="mt-1 max-w-xl text-sm leading-5 text-muted">{body}</p>
      {children}
    </div>
  );
}

export function ErrorState({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  const message = error instanceof ApiError ? error.message : "Something went wrong while loading this page.";
  const expired = error instanceof ApiError && error.status === 401;
  return (
    <div className="surface border-negative/40 px-5 py-6" role="alert">
      <h2 className="text-sm font-semibold text-foreground">{expired ? "Sign in required" : "Could not load this"}</h2>
      <p className="mt-2 text-sm leading-6 text-muted">{message}</p>
      {onRetry && !expired ? (
        <button className="button-secondary mt-4" type="button" onClick={onRetry}>
          Try again
        </button>
      ) : null}
    </div>
  );
}

export function qualityTone(quality: MarketDataQuality): string {
  switch (quality) {
    case "REAL_TIME":
      return "border-positive/40 bg-surface text-positive";
    case "DELAYED":
    case "STALE":
      return "border-warning/50 bg-surface text-warning";
    case "END_OF_DAY":
    case "UNKNOWN":
      return "border-line bg-surface text-muted";
  }
}

export function QualityBadge({ quality }: { quality: MarketDataQuality }) {
  return (
    <span className={`inline-flex border px-1 py-px text-[0.62rem] font-medium tracking-wide uppercase ${qualityTone(quality)}`}>
      {qualityLabel(quality)}
    </span>
  );
}

export function SyntheticNotice({ quotes }: { quotes: { source: MarketDataSource }[] }) {
  if (!quotes.some((quote) => quote.source === "MOCK")) {
    return null;
  }
  return (
    <p className="text-xs leading-5 text-muted">
      Prices on this screen are synthetic development fixtures. They are not live or historical market data.
    </p>
  );
}
