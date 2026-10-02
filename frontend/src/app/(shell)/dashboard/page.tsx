"use client";

import { ChangeList, CheckStatus } from "@/components/change-list";
import { EmptyState, ErrorState, LoadingState, QualityBadge, SyntheticNotice } from "@/components/states";
import { acknowledgeCheck, getChanges } from "@/lib/api/changes";
import { ApiError } from "@/lib/api/client";
import { listPortfolios } from "@/lib/api/portfolios";
import { listQuotes } from "@/lib/api/quotes";
import { listWatchlists } from "@/lib/api/watchlists";
import { changeTypeLabel, formatMoney, formatPercent, previousCloseMove } from "@/lib/format";
import type { DetectedChange, MarketQuote, Portfolio, WatchlistChanges, WatchlistSummary } from "@/lib/types";
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
  const [checkError, setCheckError] = useState<string | null>(null);
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
    const watchlistId = selectedId;
    setChecking(true);
    setCheckError(null);
    try {
      await acknowledgeCheck(watchlistId);
      const next = await getChanges(watchlistId);
      setData((current) => current == null ? current : {
        ...current,
        changesByWatchlist: { ...current.changesByWatchlist, [watchlistId]: next },
      });
    } catch (caught) {
      setCheckError(caught instanceof ApiError ? caught.message : "Could not save the check.");
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
  const elsewhere = data.watchlists
    .filter((watchlist) => watchlist.id !== selected?.id)
    .flatMap((watchlist) => (data.changesByWatchlist[watchlist.id]?.changes ?? []).map((change) => ({
      watchlist,
      change,
    })));

  return (
    <div className="grid gap-8">
      <header className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <p className="text-xs font-medium tracking-[0.18em] text-brass uppercase">Overview</p>
          <h1 className="mt-2 font-serif text-4xl text-foreground">What changed since you last checked</h1>
        </div>
        <SyntheticNotice quotes={data.quotes} />
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
          <h2 className="font-serif text-2xl">Watchlist check</h2>
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
          <EmptyState
            title="No watchlist yet"
            body="Create a watchlist and add instruments. Changes are reported against that list after you check it."
          >
            <Link className="button-primary mt-5 inline-flex" href="/watchlists">Create a watchlist</Link>
          </EmptyState>
        ) : (
          <div className="grid gap-4">
            <div>
              <p className="mb-2 text-sm text-muted">{selected.name}</p>
              <CheckStatus changes={changes} />
              <div className="mt-4 flex flex-wrap gap-2">
                <button
                  className="button-primary"
                  type="button"
                  onClick={markChecked}
                  disabled={checking}
                  aria-busy={checking}
                >
                  {checking ? "Saving check" : "Mark as checked"}
                </button>
                <Link className="button-secondary" href={`/watchlists/${selected.id}`}>Open watchlist</Link>
              </div>
              {checkError ? <p className="mt-3 text-sm text-negative" role="alert">{checkError}</p> : null}
            </div>
            {changes.changes.length === 0 ? null : <ChangeList changes={changes.changes} quotes={data.quotes} />}
          </div>
        )}
      </section>

      {elsewhere.length === 0 ? null : (
        <section className="grid gap-3">
          <h2 className="font-serif text-2xl">Recently changed</h2>
          <p className="text-sm text-muted">Changes on your other watchlists.</p>
          <ul className="grid gap-2">
            {elsewhere.map(({ watchlist, change }) => (
              <ElsewhereRow key={`${watchlist.id}-${change.instrumentId}-${change.type}`} watchlist={watchlist} change={change} />
            ))}
          </ul>
        </section>
      )}

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
                      <td className="px-3 py-3">
                        <p className="tabular-nums">{formatMoney(quote.price, quote.currency)}</p>
                        <p className="mt-1"><QualityBadge quality={quote.quality} /></p>
                      </td>
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

function ElsewhereRow({ watchlist, change }: { watchlist: WatchlistSummary; change: DetectedChange }) {
  const percent = formatPercent(change.changePercent);
  return (
    <li className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1 border-t border-line py-2 text-sm">
      <span>
        <Link href={`/watchlists/${watchlist.id}`} className="text-foreground">{watchlist.name}</Link>
        <span className="text-muted"> · </span>
        <Link href={`/instruments/${change.instrumentId}`} className="text-foreground">{change.symbol}</Link>
      </span>
      <span className="text-muted">
        {changeTypeLabel[change.type]}
        {percent ? ` · ${percent}` : ""}
      </span>
    </li>
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
