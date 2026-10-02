import { QualityBadge } from "@/components/states";
import { changeTypeLabel, formatMoney, formatPercent, formatTimestamp, severityLabel } from "@/lib/format";
import type { DetectedChange, MarketDataQuality, WatchlistChanges } from "@/lib/types";
import Link from "next/link";

export function CheckStatus({ changes }: { changes: WatchlistChanges }) {
  const title = changes.firstCheck
    ? "First check"
    : changes.changes.length === 0
      ? "No material changes"
      : "Since your last check";
  return (
    <div className="surface px-5 py-5">
      <h2 className="font-serif text-2xl">{title}</h2>
      <ul className="mt-3 grid gap-1 text-sm leading-6">
        {changes.summary.highlights.map((line) => (
          <li key={line}>{line}</li>
        ))}
      </ul>
      <p className="mt-3 text-xs text-muted">
        {changes.firstCheck
          ? "No previous check is stored. Mark this list as checked to save the current observations."
          : `Last checked ${formatTimestamp(changes.baselineCheckedAt)}`}
      </p>
    </div>
  );
}

export function ChangeList({
  changes,
  quotes,
}: {
  changes: DetectedChange[];
  quotes?: { exchange: string; symbol: string; quality: MarketDataQuality }[];
}) {
  if (changes.length === 0) {
    return null;
  }
  return (
    <ul className="grid gap-3">
      {changes.map((change) => {
        const percent = formatPercent(change.changePercent);
        const direction = change.changePercent == null ? "text-foreground" : change.changePercent < 0 ? "text-negative" : "text-positive";
        const quote = quotes?.find((item) => item.exchange === change.exchange && item.symbol === change.symbol);
        return (
          <li key={`${change.instrumentId}-${change.type}`} className="surface px-4 py-4 sm:px-5">
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div>
                <Link href={`/instruments/${change.instrumentId}`} className="font-serif text-2xl tracking-tight text-foreground">
                  {change.symbol}
                </Link>
                <p className="mt-1 text-xs tracking-wide text-muted uppercase">{change.exchange}</p>
              </div>
              <div className="text-right">
                <p className={`text-lg tabular-nums ${direction}`}>
                  {change.currency ? formatMoney(change.currentValue, change.currency) : formatMoney(change.currentValue, null)}
                </p>
                {percent ? <p className={`text-sm tabular-nums ${direction}`}>{percent}</p> : null}
                {quote ? <p className="mt-2"><QualityBadge quality={quote.quality} /></p> : null}
              </div>
            </div>
            <div className="mt-3 flex flex-wrap items-center gap-2 text-xs">
              <span className="rounded-full border border-line px-2 py-1 text-foreground">{changeTypeLabel[change.type]}</span>
              <span className={change.severity === "HIGH" ? "text-brass" : "text-muted"}>{severityLabel(change.severity)}</span>
            </div>
            <p className="mt-3 text-sm leading-6 text-foreground">{change.message}</p>
            <p className="mt-2 text-xs text-muted">Detected {formatTimestamp(change.detectedAt)}</p>
          </li>
        );
      })}
    </ul>
  );
}
