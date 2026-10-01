import { changeTypeLabel, formatMoney, formatPercent, formatTimestamp, severityLabel } from "@/lib/format";
import type { DetectedChange } from "@/lib/types";
import Link from "next/link";

export function ChangeList({ changes }: { changes: DetectedChange[] }) {
  if (changes.length === 0) {
    return null;
  }
  return (
    <ul className="grid gap-3">
      {changes.map((change) => {
        const percent = formatPercent(change.changePercent);
        const direction = change.changePercent == null ? "text-foreground" : change.changePercent < 0 ? "text-negative" : "text-positive";
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
              </div>
            </div>
            <div className="mt-3 flex flex-wrap items-center gap-2 text-xs">
              <span className="rounded-full border border-line px-2 py-1 text-foreground">{changeTypeLabel[change.type]}</span>
              <span className={change.severity === "HIGH" ? "text-brass" : "text-muted"}>{severityLabel(change.severity)}</span>
              <span className="text-muted">{formatTimestamp(change.detectedAt)}</span>
            </div>
            <p className="mt-3 text-sm leading-6 text-foreground">{change.message}</p>
          </li>
        );
      })}
    </ul>
  );
}
