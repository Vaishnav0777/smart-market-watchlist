"use client";

import { ChangeList, CheckStatus } from "@/components/change-list";
import { EmptyState, ErrorState, LoadingState, QualityBadge, SyntheticNotice } from "@/components/states";
import { acknowledgeCheck, getChanges } from "@/lib/api/changes";
import { ApiError } from "@/lib/api/client";
import { getPortfolioAnalytics, listPortfolios } from "@/lib/api/portfolios";
import { listQuotes } from "@/lib/api/quotes";
import { listWatchlists } from "@/lib/api/watchlists";
import { changeTypeLabel, directionMark, formatMoney, formatPercent, formatSignedMoney, formatVolume, moveTone, previousCloseMove, priceDelta } from "@/lib/format";
import type { DetectedChange, MarketQuote, Portfolio, PortfolioAnalytics, WatchlistChanges, WatchlistSummary } from "@/lib/types";
import Link from "next/link";
import { useEffect, useState } from "react";

type DashboardData = {
  watchlists: WatchlistSummary[];
  quotes: MarketQuote[];
  portfolios: Portfolio[];
  analyticsByPortfolio: Record<string, PortfolioAnalytics | null>;
  changesByWatchlist: Record<string, WatchlistChanges>;
};

export default function DashboardPage() {
  const [data, setData] = useState<DashboardData | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [portfolioId, setPortfolioId] = useState<string | null>(null);
  const [checking, setChecking] = useState(false);
  const [checkError, setCheckError] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    let cancelled = false;
    Promise.all([listWatchlists(), listQuotes(), listPortfolios()])
      .then(async ([watchlists, quotes, portfolios]) => {
        const [entries, analyticsEntries] = await Promise.all([
          Promise.all(watchlists.map(async (watchlist) => {
            const changes = await getChanges(watchlist.id);
            return [watchlist.id, changes] as const;
          })),
          Promise.all(portfolios.map(async (portfolio) => {
            try {
              return [portfolio.id, await getPortfolioAnalytics(portfolio.id)] as const;
            } catch {
              return [portfolio.id, null] as const;
            }
          })),
        ]);
        if (cancelled) {
          return;
        }
        setData({
          watchlists,
          quotes,
          portfolios,
          analyticsByPortfolio: Object.fromEntries(analyticsEntries),
          changesByWatchlist: Object.fromEntries(entries),
        });
        setSelectedId((current) => current && watchlists.some((watchlist) => watchlist.id === current)
          ? current
          : watchlists[0]?.id ?? null);
        setPortfolioId((current) => current && portfolios.some((portfolio) => portfolio.id === current)
          ? current
          : portfolios[0]?.id ?? null);
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
  const book = data.portfolios.find((portfolio) => portfolio.id === portfolioId) ?? null;
  const analytics = book ? data.analyticsByPortfolio[book.id] ?? null : null;
  const elsewhere = data.watchlists
    .filter((watchlist) => watchlist.id !== selected?.id)
    .flatMap((watchlist) => (data.changesByWatchlist[watchlist.id]?.changes ?? []).map((change) => ({
      watchlist,
      change,
    })));

  return (
    <div className="grid gap-5">
      <header className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-lg font-semibold text-foreground">Overview</h1>
        </div>
        <SyntheticNotice quotes={data.quotes} />
      </header>

      <PortfolioStrip
        portfolios={data.portfolios}
        selectedId={portfolioId}
        analytics={analytics}
        onSelect={setPortfolioId}
      />

      <section className="grid gap-2">
        <div className="flex flex-wrap items-end justify-between gap-3">
          <div>
            <h2 className="text-sm font-semibold">What changed since your last check</h2>
            <p className="text-xs text-muted">Reported only after you mark a watchlist as checked.</p>
          </div>
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
          <h2 className="text-sm font-semibold">Recently changed</h2>
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
          <h2 className="text-sm font-semibold">Market overview</h2>
          <div className="surface mt-3 overflow-x-auto">
            <table className="market-table min-w-[44rem]">
              <thead>
                <tr>
                  <th>Symbol</th>
                  <th>Instrument</th>
                  <th className="num">LTP</th>
                  <th className="num">Change</th>
                  <th className="num">Change %</th>
                  <th className="num">Volume</th>
                  <th>Quality</th>
                </tr>
              </thead>
              <tbody>
                {data.quotes.map((quote) => {
                  const move = previousCloseMove(quote.price, quote.previousClose);
                  const delta = priceDelta(quote.price, quote.previousClose);
                  const tone = moveTone(move);
                  return (
                    <tr key={`${quote.exchange}-${quote.symbol}`}>
                      <td className="font-semibold">{quote.symbol}</td>
                      <td>
                        <p>{quote.companyName}</p>
                        <p className="text-xs text-muted">{quote.exchange}</p>
                      </td>
                      <td className="num">{formatMoney(quote.price, quote.currency)}</td>
                      <td className={`num ${tone}`}>{formatSignedMoney(delta, quote.currency)}</td>
                      <td className={`num ${tone}`}>{formatPercent(move) ?? "—"} {directionMark(move)}</td>
                      <td className="num">{formatVolume(quote.volume)}</td>
                      <td><QualityBadge quality={quote.quality} /></td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
        <div className="surface h-fit px-4 py-3">
          <h2 className="text-sm font-semibold">Quick actions</h2>
          <div className="mt-2 grid gap-1.5">
            <Link className="text-sm text-foreground underline" href="/watchlists">Manage watchlists</Link>
            <Link className="text-sm text-foreground underline" href="/search">Search instruments</Link>
            <Link className="text-sm text-foreground underline" href="/portfolio">Review portfolio</Link>
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

function PortfolioStrip({
  portfolios,
  selectedId,
  analytics,
  onSelect,
}: {
  portfolios: Portfolio[];
  selectedId: string | null;
  analytics: PortfolioAnalytics | null;
  onSelect: (id: string) => void;
}) {
  if (portfolios.length === 0) {
    return (
      <EmptyState title="No portfolio yet" body="Create a portfolio and record a holding. Value, invested amount, and P&L come from that book's analytics.">
        <Link className="button-primary mt-4 inline-flex" href="/portfolio">Create a portfolio</Link>
      </EmptyState>
    );
  }
  const currency = analytics?.currency ?? null;
  const name = portfolios.find((portfolio) => portfolio.id === selectedId)?.name ?? portfolios[0]?.name;
  return (
    <section className="border-b border-line pb-4">
      <div className="flex flex-wrap items-baseline justify-between gap-3">
        <p className="text-xs text-muted">Portfolio</p>
        {portfolios.length > 1 ? (
          <select className="field w-auto" value={selectedId ?? ""} onChange={(event) => onSelect(event.target.value)}>
            {portfolios.map((portfolio) => (
              <option key={portfolio.id} value={portfolio.id}>{portfolio.name}</option>
            ))}
          </select>
        ) : null}
      </div>
      <h2 className="text-base font-semibold">{name}</h2>
      {analytics?.mixedCurrencies ? (
        <p className="mt-2 text-xs text-muted">This book uses more than one currency, so invested amount, value, P&L, and return are not combined.</p>
      ) : null}
      {!analytics ? <p className="mt-2 text-xs text-muted">Portfolio analytics are not available for this book.</p> : null}
      <div className="mt-3 flex flex-wrap items-end gap-x-8 gap-y-2">
        <div>
          <p className="text-2xl font-semibold tabular-nums tracking-tight">{formatMoney(analytics?.currentValue, currency)}</p>
          <p className="text-xs text-muted">Current value</p>
        </div>
        <p className="pb-1 text-sm tabular-nums text-foreground">{formatMoney(analytics?.totalInvested, currency)} invested</p>
        <div>
          <p className="text-base font-semibold tabular-nums">
            <span className={moveTone(analytics?.totalPnl)}>{formatSignedMoney(analytics?.totalPnl, currency)}</span>
            {" "}
            <span className={moveTone(analytics?.returnPercent)}>
              {formatPercent(analytics?.returnPercent) ?? "—"} {directionMark(analytics?.returnPercent)}
            </span>
          </p>
          <p className="text-xs text-muted">Unrealized P&L</p>
        </div>
      </div>
    </section>
  );
}
