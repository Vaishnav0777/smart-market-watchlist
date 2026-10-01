"use client";

import { ChangeList } from "@/components/change-list";
import { ErrorState, LoadingState, SyntheticNotice } from "@/components/states";
import { acknowledgeCheck, getChanges } from "@/lib/api/changes";
import { listPortfolios } from "@/lib/api/portfolios";
import { listQuotes } from "@/lib/api/quotes";
import { listWatchlists } from "@/lib/api/watchlists";
import { formatMoney, formatPercent, formatTimestamp, previousCloseMove } from "@/lib/format";
import type { MarketQuote, Portfolio, WatchlistChanges, WatchlistSummary } from "@/lib/types";
import Link from "next/link";
import { useEffect, useState } from "react";

type DashboardData = {
  watchlists: WatchlistSummary[];
  quotes: MarketQuote[];
  portfolios: Portfolio[];
  changesByWatchlist: Record<string, WatchlistChanges>;
};

export default function DashboardPage() {
  const [data, setData] = useState<DashboardData | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [checking, setChecking] = useState(false);
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    let cancelled = false;
    Promise.all([listWatchlists(), listQuotes(), listPortfolios()])
      .then(async ([watchlists, quotes, portfolios]) => {
        const entries = await Promise.all(watchlists.map(async (watchlist) => {
          const changes = await getChanges(watchlist.id);
          return [watchlist.id, changes] as const;
        }));
        if (cancelled) {
          return;
        }
        setData({
          watchlists,
          quotes,
          portfolios,
          changesByWatchlist: Object.fromEntries(entries),
        });
        setSelectedId((current) => current && watchlists.some((watchlist) => watchlist.id === current)
          ? current
          : watchlists[0]?.id ?? null);
      })
      .catch((caught) => {
        if (!cancelled) {
          setError(caught);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [reloadKey]);

  async function markChecked() {
    if (!selectedId) {
      return;
    }
    setChecking(true);
    try {
      await acknowledgeCheck(selectedId);
      setReloadKey((value) => value + 1);
    } catch (caught) {
      setError(caught);
    } finally {
      setChecking(false);
    }
  }

  if (error) {
    return <ErrorState error={error} onRetry={() => { setError(null); setReloadKey((value) => value + 1); }} />;
  }
  if (!data) {
    return <LoadingState label="Loading your dashboard" />;
  }

  const selected = data.watchlists.find((watchlist) => watchlist.id === selectedId) ?? null;
  const changes = selected ? data.changesByWatchlist[selected.id] : null;
  const positionCount = data.portfolios.reduce((sum, portfolio) => sum + portfolio.positions.length, 0);
  const recent = Object.values(data.changesByWatchlist)
    .flatMap((entry) => entry.changes)
    .slice(0, 4);

  return (
    <div className="grid gap-8">
      <header className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <p className="text-xs font-medium tracking-[0.18em] text-brass uppercase">Overview</p>
          <h1 className="mt-2 font-serif text-4xl text-foreground">What changed since you last checked</h1>
        </div>
        <SyntheticNotice />
      </header>

      <section className="grid gap-3 sm:grid-cols-3">
        <SummaryCard label="Watchlists" value={String(data.watchlists.length)} href="/watchlists" />
        <SummaryCard label="Holdings" value={String(positionCount)} href="/portfolio" />
        <SummaryCard
          label="Meaningful changes"
          value={String(Object.values(data.changesByWatchlist).reduce((sum, entry) => sum + entry.summary.totalChanges, 0))}
          href="/watchlists"
        />
      </section>

      <section className="grid gap-4">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <h2 className="font-serif text-2xl">Since your last check</h2>
          {data.watchlists.length > 1 ? (
            <label className="text-sm text-muted">
              Watchlist
              <select
                className="field mt-1"
                value={selectedId ?? ""}
                onChange={(event) => setSelectedId(event.target.value)}
              >
                {data.watchlists.map((watchlist) => (
                  <option key={watchlist.id} value={watchlist.id}>{watchlist.name}</option>
                ))}
              </select>
            </label>
          ) : null}
        </div>
        {!selected || !changes ? (
          <div className="surface px-5 py-8">
            <h3 className="font-serif text-2xl">No watchlist yet</h3>
            <p className="mt-2 max-w-xl text-sm leading-6 text-muted">
              Create a watchlist and add instruments. Changes are reported against that list after you check it.
            </p>
            <Link className="button-primary mt-5 inline-flex" href="/watchlists">Create a watchlist</Link>
          </div>
        ) : (
          <div className="grid gap-4">
            <div className="surface px-5 py-5">
              <p className="text-sm text-muted">{selected.name}</p>
              <ul className="mt-3 grid gap-1 text-sm leading-6">
                {changes.summary.highlights.map((line) => (
                  <li key={line}>{line}</li>
                ))}
              </ul>
              <p className="mt-3 text-xs text-muted">
                {changes.baselineCheckedAt
                  ? `Baseline ${formatTimestamp(changes.baselineCheckedAt)}`
                  : "No check has been recorded yet."}
              </p>
              {changes.cursor ? <p className="mt-1 text-xs text-muted">Cursor saved with this check.</p> : null}
              <div className="mt-4 flex flex-wrap gap-2">
                <button className="button-primary" type="button" onClick={markChecked} disabled={checking}>
                  {checking ? "Saving check" : "Mark as checked"}
                </button>
                <Link className="button-secondary" href={`/watchlists/${selected.id}`}>Open watchlist</Link>
              </div>
            </div>
            {changes.changes.length === 0 ? (
              <p className="text-sm text-muted">The backend did not report a meaningful change for this list.</p>
            ) : (
              <ChangeList changes={changes.changes} />
            )}
          </div>
        )}
      </section>

      <section className="grid gap-3">
        <h2 className="font-serif text-2xl">Recently changed</h2>
        {recent.length === 0 ? (
          <p className="text-sm text-muted">Nothing is flagged across your watchlists.</p>
        ) : (
          <ChangeList changes={recent} />
        )}
      </section>

      <section className="grid gap-3 lg:grid-cols-[minmax(0,1.4fr)_minmax(16rem,0.8fr)]">
        <div>
          <h2 className="font-serif text-2xl">Market overview</h2>
          <div className="mt-3 overflow-x-auto">
            <table className="w-full min-w-[36rem] text-left text-sm">
              <thead className="text-xs tracking-wide text-muted uppercase">
                <tr>
                  <th className="px-3 py-2 font-medium">Instrument</th>
                  <th className="px-3 py-2 font-medium">Price</th>
                  <th className="px-3 py-2 font-medium">Vs previous close</th>
                </tr>
              </thead>
              <tbody>
                {data.quotes.map((quote) => {
                  const move = previousCloseMove(quote.price, quote.previousClose);
                  const tone = move == null ? "text-muted" : move < 0 ? "text-negative" : "text-positive";
                  return (
                    <tr key={`${quote.exchange}-${quote.symbol}`} className="border-t border-line">
                      <td className="px-3 py-3">
                        <p className="text-foreground">{quote.symbol}</p>
                        <p className="text-xs text-muted">{quote.companyName}</p>
                      </td>
                      <td className="px-3 py-3 tabular-nums">{formatMoney(quote.price, quote.currency)}</td>
                      <td className={`px-3 py-3 tabular-nums ${tone}`}>{formatPercent(move) ?? "—"}</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
        <div className="surface h-fit px-5 py-5">
          <h2 className="font-serif text-2xl">Quick actions</h2>
          <div className="mt-4 grid gap-2">
            <Link className="button-secondary" href="/watchlists">Manage watchlists</Link>
            <Link className="button-secondary" href="/search">Search instruments</Link>
            <Link className="button-secondary" href="/portfolio">Review portfolio</Link>
          </div>
        </div>
      </section>
    </div>
  );
}

function SummaryCard({ label, value, href }: { label: string; value: string; href: string }) {
  return (
    <Link href={href} className="surface px-4 py-4">
      <p className="text-xs tracking-wide text-muted uppercase">{label}</p>
      <p className="mt-2 font-serif text-3xl tabular-nums">{value}</p>
    </Link>
  );
}
