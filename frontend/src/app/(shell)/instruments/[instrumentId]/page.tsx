"use client";

import { ChangeList } from "@/components/change-list";
import { ErrorState, LoadingState, QualityBadge, SyntheticNotice } from "@/components/states";
import { getChanges } from "@/lib/api/changes";
import { getInstrument, getInstrumentMemberships } from "@/lib/api/instruments";
import { formatMoney, formatPercent, formatTimestamp, formatVolume, previousCloseMove } from "@/lib/format";
import type { DetectedChange, InstrumentDetail, InstrumentMembership } from "@/lib/types";
import type { ReactNode } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { useCallback, useEffect, useState } from "react";

type DetailState = {
  detail: InstrumentDetail;
  memberships: InstrumentMembership[];
  changes: DetectedChange[];
};

export default function InstrumentPage() {
  const params = useParams<{ instrumentId: string }>();
  const instrumentId = params.instrumentId;
  const [state, setState] = useState<DetailState | null>(null);
  const [error, setError] = useState<unknown>(null);

  const load = useCallback((reset: boolean) => {
    if (reset) {
      setError(null);
      setState(null);
    }
    Promise.all([getInstrument(instrumentId), getInstrumentMemberships(instrumentId)])
      .then(async ([detail, memberships]) => {
        const grouped = await Promise.all(memberships.map((membership) => getChanges(membership.watchlistId)));
        const changes = grouped
          .flatMap((entry) => entry.changes)
          .filter((change) => change.instrumentId === instrumentId);
        setState({ detail, memberships, changes });
        setError(null);
      })
      .catch(setError);
  }, [instrumentId]);

  useEffect(() => {
    queueMicrotask(() => load(false));
  }, [load]);

  if (error) {
    return <ErrorState error={error} onRetry={() => load(true)} />;
  }
  if (!state) {
    return <LoadingState label="Loading instrument" />;
  }

  const { detail, memberships, changes } = state;
  const quote = detail.quote;
  const move = quote ? previousCloseMove(quote.price, quote.previousClose) : null;
  const tone = move == null ? "text-muted" : move < 0 ? "text-negative" : "text-positive";

  return (
    <div className="grid gap-6">
      <header>
        <p className="text-xs text-muted">{detail.instrument.exchange} · {detail.instrument.instrumentType}</p>
        <h1 className="text-lg font-semibold">{detail.instrument.symbol}</h1>
        <p className="mt-2 text-muted">{detail.instrument.displayName}</p>
        <p className="mt-1 text-sm text-muted">{detail.instrument.sector}</p>
      </header>
      <SyntheticNotice quotes={quote ? [quote] : []} />

      {!quote ? (
        <div className="surface px-5 py-8">
          <h2 className="text-sm font-semibold">No quote</h2>
          <p className="mt-2 text-sm text-muted">The market-data provider has no quote for this exchange and symbol.</p>
        </div>
      ) : (
        <section className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          <Metric label="Last price" value={formatMoney(quote.price, quote.currency)} detail={formatPercent(move) ?? "No previous close"} tone={tone} badge={<QualityBadge quality={quote.quality} />} />
          <Metric label="Previous close" value={formatMoney(quote.previousClose, quote.currency)} />
          <Metric label="Open" value={formatMoney(quote.open, quote.currency)} />
          <Metric label="Volume" value={formatVolume(quote.volume)} />
          <Metric label="Day high" value={formatMoney(quote.high, quote.currency)} />
          <Metric label="Day low" value={formatMoney(quote.low, quote.currency)} />
          <Metric label="Session" value={quote.sessionDate ?? "—"} />
          <Metric label="Observed" value={formatTimestamp(quote.timestamp)} />
        </section>
      )}

      <section className="grid gap-3">
        <h2 className="text-sm font-semibold">Watchlist membership</h2>
        {memberships.length === 0 ? (
          <p className="text-sm text-muted">This instrument is not on one of your watchlists. <Link className="underline" href="/search">Search and add it</Link>.</p>
        ) : (
          <ul className="grid gap-2">
            {memberships.map((membership) => (
              <li key={membership.itemId}>
                <Link className="text-foreground underline" href={`/watchlists/${membership.watchlistId}`}>{membership.watchlistName}</Link>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="grid gap-3">
        <h2 className="text-sm font-semibold">Meaningful changes</h2>
        <p className="text-sm leading-6 text-muted">
          These are the changes the backend already reported for watchlists that contain this instrument.
        </p>
        {changes.length === 0 ? (
          <p className="text-sm text-muted">No meaningful change is reported for this instrument.</p>
        ) : (
          <ChangeList changes={changes} />
        )}
      </section>
    </div>
  );
}

function Metric({ label, value, detail, tone, badge }: { label: string; value: string; detail?: string; tone?: string; badge?: ReactNode }) {
  return (
    <div className="surface px-3 py-2">
      <p className="text-xs text-muted">{label}</p>
      <p className="text-base font-semibold tabular-nums">{value}</p>
      {detail ? <p className={`mt-1 text-sm tabular-nums ${tone ?? "text-muted"}`}>{detail}</p> : null}
      {badge ? <p className="mt-2">{badge}</p> : null}
    </div>
  );
}
