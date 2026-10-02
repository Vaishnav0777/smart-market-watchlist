"use client";

import { ChangeList, CheckStatus } from "@/components/change-list";
import { ErrorState, LoadingState, QualityBadge, SyntheticNotice } from "@/components/states";
import { acknowledgeCheck, getChanges } from "@/lib/api/changes";
import { ApiError } from "@/lib/api/client";
import { getWatchlist, removeWatchlistItem, reorderWatchlistItems } from "@/lib/api/watchlists";
import { formatMoney, formatPercent, formatTimestamp, previousCloseMove } from "@/lib/format";
import type { WatchlistChanges, WatchlistDetail } from "@/lib/types";
import Link from "next/link";
import { useParams } from "next/navigation";
import { useCallback, useEffect, useState } from "react";

export default function WatchlistDetailPage() {
  const params = useParams<{ watchlistId: string }>();
  const watchlistId = params.watchlistId;
  const [watchlist, setWatchlist] = useState<WatchlistDetail | null>(null);
  const [changes, setChanges] = useState<WatchlistChanges | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [checking, setChecking] = useState(false);

  const load = useCallback((reset: boolean) => {
    if (reset) {
      setError(null);
      setWatchlist(null);
      setChanges(null);
    }
    Promise.all([getWatchlist(watchlistId), getChanges(watchlistId)])
      .then(([nextWatchlist, nextChanges]) => {
        setWatchlist(nextWatchlist);
        setChanges(nextChanges);
        setError(null);
      })
      .catch(setError);
  }, [watchlistId]);

  useEffect(() => {
    queueMicrotask(() => load(false));
  }, [load]);

  async function move(index: number, direction: -1 | 1) {
    if (!watchlist) {
      return;
    }
    const target = index + direction;
    if (target < 0 || target >= watchlist.items.length) {
      return;
    }
    const ids = watchlist.items.map((item) => item.id);
    const [moved] = ids.splice(index, 1);
    ids.splice(target, 0, moved);
    try {
      const next = await reorderWatchlistItems(watchlist.id, ids);
      setWatchlist(next);
      setActionError(null);
    } catch (caught) {
      setActionError(caught instanceof ApiError ? caught.message : "Could not reorder the list.");
    }
  }

  async function remove(itemId: string) {
    if (!watchlist) {
      return;
    }
    try {
      await removeWatchlistItem(watchlist.id, itemId);
      load(true);
    } catch (caught) {
      setActionError(caught instanceof ApiError ? caught.message : "Could not remove the instrument.");
    }
  }

  async function markChecked() {
    setChecking(true);
    try {
      await acknowledgeCheck(watchlistId);
      const next = await getChanges(watchlistId);
      setChanges(next);
      setActionError(null);
    } catch (caught) {
      setActionError(caught instanceof ApiError ? caught.message : "Could not save the check.");
    } finally {
      setChecking(false);
    }
  }

  if (error) {
    return <ErrorState error={error} onRetry={() => load(true)} />;
  }
  if (!watchlist || !changes) {
    return <LoadingState label="Loading watchlist" />;
  }

  return (
    <div className="grid gap-6">
      <header className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <Link href="/watchlists" className="text-xs tracking-[0.16em] text-brass uppercase">Watchlists</Link>
          <h1 className="mt-2 font-serif text-4xl">{watchlist.name}</h1>
          <p className="mt-2 text-sm text-muted">{watchlist.items.length} {watchlist.items.length === 1 ? "instrument" : "instruments"}</p>
        </div>
        <div className="flex flex-wrap gap-2">
          <Link className="button-primary" href={`/search?watchlist=${watchlist.id}`}>Add instrument</Link>
          <button className="button-secondary" type="button" onClick={markChecked} disabled={checking}>
            {checking ? "Saving check" : "Mark as checked"}
          </button>
        </div>
      </header>
      <SyntheticNotice quotes={watchlist.items.flatMap((item) => item.quote ? [item.quote] : [])} />
      {actionError ? <p className="text-sm text-negative" role="alert">{actionError}</p> : null}

      {watchlist.items.length === 0 ? (
        <div className="surface px-5 py-8">
          <h2 className="font-serif text-2xl">This watchlist is empty</h2>
          <p className="mt-2 text-sm leading-6 text-muted">Search the instrument directory and add a symbol to this list.</p>
        </div>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full min-w-[44rem] text-left text-sm">
            <thead className="text-xs tracking-wide text-muted uppercase">
              <tr>
                <th className="px-3 py-2 font-medium">Instrument</th>
                <th className="px-3 py-2 font-medium">Price</th>
                <th className="px-3 py-2 font-medium">Vs previous close</th>
                <th className="px-3 py-2 font-medium">Order</th>
              </tr>
            </thead>
            <tbody>
              {watchlist.items.map((item, index) => {
                const movePercent = item.quote ? previousCloseMove(item.quote.price, item.quote.previousClose) : null;
                const tone = movePercent == null ? "text-muted" : movePercent < 0 ? "text-negative" : "text-positive";
                return (
                  <tr key={item.id} className="border-t border-line">
                    <td className="px-3 py-3">
                      <Link href={`/instruments/${item.instrument.id}`} className="text-foreground">{item.instrument.symbol}</Link>
                      <p className="text-xs text-muted">{item.instrument.displayName} · {item.instrument.exchange}</p>
                    </td>
                    <td className="px-3 py-3 tabular-nums">
                      {item.quote ? formatMoney(item.quote.price, "INR") : "No quote"}
                      {item.quote ? <p className="mt-1"><QualityBadge quality={item.quote.quality} /></p> : null}
                      {item.quote ? <p className="text-xs text-muted">{formatTimestamp(item.quote.timestamp)}</p> : null}
                    </td>
                    <td className={`px-3 py-3 tabular-nums ${tone}`}>{formatPercent(movePercent) ?? "—"}</td>
                    <td className="px-3 py-3">
                      <div className="flex gap-2">
                        <button className="button-secondary" type="button" onClick={() => move(index, -1)} disabled={index === 0}>Up</button>
                        <button className="button-secondary" type="button" onClick={() => move(index, 1)} disabled={index === watchlist.items.length - 1}>Down</button>
                        <button className="button-secondary" type="button" onClick={() => remove(item.id)}>Remove</button>
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}

      <section className="grid gap-3">
        <CheckStatus changes={changes} />
        <ChangeList
          changes={changes.changes}
          quotes={watchlist.items.flatMap((item) => item.quote ? [item.quote] : [])}
        />
      </section>
    </div>
  );
}
