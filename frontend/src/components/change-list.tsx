import { QualityBadge } from "@/components/states";
import { changeTypeLabel, directionMark, formatClock, formatMoney, formatPercent, formatSignedMoney, formatTimestamp, moveTone, severityLabel } from "@/lib/format";
import type { DetectedChange, MarketDataQuality, WatchlistChanges } from "@/lib/types";
import Link from "next/link";

export function CheckStatus({ changes }: { changes: WatchlistChanges }) {
  const title = changes.firstCheck
    ? "First check"
    : changes.changes.length === 0
      ? "No material changes"
      : "Since your last check";
  return (
    <div className="text-sm">
      <p className="font-medium text-foreground">{title}</p>
      <ul className="mt-1 grid gap-0.5 text-muted">
        {changes.summary.highlights.map((line) => (
          <li key={line}>{line}</li>
        ))}
      </ul>
      <p className="mt-1 text-xs text-muted">
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
    <div className="surface overflow-x-auto">
      <table className="market-table min-w-[40rem]">
        <thead>
          <tr>
            <th>Symbol</th>
            <th className="num">Price</th>
            <th className="num">Change</th>
            <th className="num">Change %</th>
            <th>Time</th>
            <th>Quality</th>
          </tr>
        </thead>
        <tbody>
          {changes.map((change) => {
            const quote = quotes?.find((item) => item.exchange === change.exchange && item.symbol === change.symbol);
            const tone = moveTone(change.changePercent);
            return (
              <tr key={`${change.instrumentId}-${change.type}`}>
                <td>
                  <Link href={`/instruments/${change.instrumentId}`} className="font-semibold text-foreground">{change.symbol}</Link>
                  <p className="text-xs text-muted">{changeTypeLabel[change.type]} · {severityLabel(change.severity)}</p>
                </td>
                <td className="num">{formatMoney(change.currentValue, change.currency)}</td>
                <td className={`num ${tone}`}>{formatSignedMoney(change.absoluteChange, change.currency)}</td>
                <td className={`num ${tone}`}>
                  {formatPercent(change.changePercent) ?? "—"} {directionMark(change.changePercent)}
                </td>
                <td className="text-xs text-muted whitespace-nowrap" title={formatTimestamp(change.detectedAt)}>
                  {formatClock(change.detectedAt)}
                </td>
                <td>{quote ? <QualityBadge quality={quote.quality} /> : <span className="text-xs text-muted">—</span>}</td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
