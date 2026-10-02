"use client";

import { ErrorState, LoadingState, QualityBadge, SyntheticNotice } from "@/components/states";
import { ApiError } from "@/lib/api/client";
import { searchInstruments } from "@/lib/api/instruments";
import { addWatchlistItem, listWatchlists } from "@/lib/api/watchlists";
import { formatMoney, formatPercent, previousCloseMove } from "@/lib/format";
import type { InstrumentListing, WatchlistSummary } from "@/lib/types";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Suspense, useEffect, useState } from "react";

export default function SearchPage() {
  return (
    <Suspense fallback={<LoadingState label="Loading search" />}>
      <SearchScreen />
    </Suspense>
  );
}

function SearchScreen() {
  const searchParams = useSearchParams();
  const requestedWatchlist = searchParams.get("watchlist");
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<InstrumentListing[] | null>(null);
  const [watchlists, setWatchlists] = useState<WatchlistSummary[] | null>(null);
  const [watchlistId, setWatchlistId] = useState(requestedWatchlist ?? "");
  const [error, setError] = useState<unknown>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [pendingId, setPendingId] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    listWatchlists()
      .then((next) => {
        if (cancelled) {
          return;
        }
        setWatchlists(next);
        setWatchlistId((current) => current || next[0]?.id || "");
      })
      .catch((caught) => {
        if (!cancelled) {
          setError(caught);
        }
      });
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    let cancelled = false;
    const handle = window.setTimeout(() => {
      searchInstruments(query)
        .then((next) => {
          if (!cancelled) {
            setResults(next);
            setError(null);
          }
        })
        .catch((caught) => {
          if (!cancelled) {
            setError(caught);
          }
        });
    }, 250);
    return () => {
      cancelled = true;
      window.clearTimeout(handle);
    };
  }, [query]);

  async function add(listing: InstrumentListing) {
    if (!watchlistId) {
      setNotice("Create a watchlist before adding an instrument.");
      return;
    }
    setPendingId(listing.instrument.id);
    setNotice(null);
    try {
      await addWatchlistItem(watchlistId, listing.instrument.id);
      const name = watchlists?.find((watchlist) => watchlist.id === watchlistId)?.name ?? "the watchlist";
      setNotice(`${listing.instrument.symbol} was added to ${name}.`);
    } catch (caught) {
      setNotice(caught instanceof ApiError ? caught.message : "Could not add the instrument.");
    } finally {
      setPendingId(null);
    }
  }

  if (error && !results) {
    return <ErrorState error={error} onRetry={() => setQuery((value) => value)} />;
  }

  return (
    <div className="grid gap-6">
      <header>
        <p className="text-xs font-medium tracking-[0.18em] text-brass uppercase">Directory</p>
        <h1 className="mt-2 font-serif text-4xl">Search instruments</h1>
        <p className="mt-2 max-w-xl text-sm leading-6 text-muted">
          Results come from the backend instrument directory. The catalog is not stored in the browser.
        </p>
      </header>
      <SyntheticNotice quotes={results?.map((listing) => listing.quote) ?? []} />
      <div className="grid gap-3 sm:grid-cols-[minmax(0,1fr)_16rem]">
        <label className="grid gap-1 text-sm">
          Symbol or company
          <input className="field" value={query} onChange={(event) => setQuery(event.target.value)} placeholder="RELIANCE" />
        </label>
        <label className="grid gap-1 text-sm">
          Add to
          <select className="field" value={watchlistId} onChange={(event) => setWatchlistId(event.target.value)}>
            {watchlists?.length ? watchlists.map((watchlist) => (
              <option key={watchlist.id} value={watchlist.id}>{watchlist.name}</option>
            )) : <option value="">No watchlist</option>}
          </select>
        </label>
      </div>
      {notice ? <p className="text-sm text-foreground" role="status">{notice}</p> : null}
      {error ? <ErrorState error={error} /> : null}
      {!results ? <LoadingState label="Searching instruments" /> : null}
      {results && results.length === 0 ? (
        <div className="surface px-5 py-8">
          <h2 className="font-serif text-2xl">No matches</h2>
          <p className="mt-2 text-sm text-muted">Try another symbol or company name from the synthetic directory.</p>
        </div>
      ) : null}
      {results && results.length > 0 ? (
        <ul className="grid gap-3">
          {results.map((listing) => {
            const move = previousCloseMove(listing.quote.price, listing.quote.previousClose);
            const tone = move == null ? "text-muted" : move < 0 ? "text-negative" : "text-positive";
            return (
              <li key={listing.instrument.id} className="surface flex flex-wrap items-center justify-between gap-3 px-4 py-4">
                <div>
                  <Link href={`/instruments/${listing.instrument.id}`} className="font-serif text-2xl">{listing.instrument.symbol}</Link>
                  <p className="text-sm text-muted">{listing.instrument.displayName} · {listing.instrument.exchange}</p>
                </div>
                <div className="text-right">
                  <p className="tabular-nums">{formatMoney(listing.quote.price, listing.quote.currency)}</p>
                  <p className={`text-sm tabular-nums ${tone}`}>{formatPercent(move) ?? "—"}</p>
                  <p className="mt-1"><QualityBadge quality={listing.quote.quality} /></p>
                </div>
                <button className="button-primary" type="button" disabled={pendingId === listing.instrument.id} onClick={() => add(listing)}>
                  {pendingId === listing.instrument.id ? "Adding" : "Add"}
                </button>
              </li>
            );
          })}
        </ul>
      ) : null}
    </div>
  );
}
