"use client";

import { ErrorState, LoadingState } from "@/components/states";
import { ApiError } from "@/lib/api/client";
import { createWatchlist, deleteWatchlist, listWatchlists, renameWatchlist } from "@/lib/api/watchlists";
import type { WatchlistSummary } from "@/lib/types";
import Link from "next/link";
import { useEffect, useState } from "react";

export default function WatchlistsPage() {
  const [watchlists, setWatchlists] = useState<WatchlistSummary[] | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [name, setName] = useState("");
  const [formError, setFormError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  function load() {
    setError(null);
    setWatchlists(null);
    listWatchlists().then(setWatchlists).catch(setError);
  }

  useEffect(() => {
    let cancelled = false;
    listWatchlists()
      .then((next) => {
        if (!cancelled) {
          setWatchlists(next);
        }
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

  async function onCreate(event: React.FormEvent) {
    event.preventDefault();
    const trimmed = name.trim();
    if (!trimmed || trimmed.length > 120) {
      setFormError("Use a name between 1 and 120 characters.");
      return;
    }
    setPending(true);
    setFormError(null);
    try {
      await createWatchlist(trimmed);
      setName("");
      load();
    } catch (caught) {
      setFormError(caught instanceof ApiError ? caught.message : "Could not create the watchlist.");
    } finally {
      setPending(false);
    }
  }

  async function onRename(watchlist: WatchlistSummary) {
    const next = window.prompt("Watchlist name", watchlist.name);
    if (next == null) {
      return;
    }
    const trimmed = next.trim();
    if (!trimmed || trimmed.length > 120) {
      setFormError("Use a name between 1 and 120 characters.");
      return;
    }
    try {
      await renameWatchlist(watchlist.id, trimmed);
      load();
    } catch (caught) {
      setFormError(caught instanceof ApiError ? caught.message : "Could not rename the watchlist.");
    }
  }

  async function onDelete(watchlist: WatchlistSummary) {
    if (!window.confirm(`Delete ${watchlist.name}? This also removes its check history.`)) {
      return;
    }
    try {
      await deleteWatchlist(watchlist.id);
      load();
    } catch (caught) {
      setFormError(caught instanceof ApiError ? caught.message : "Could not delete the watchlist.");
    }
  }

  if (error) {
    return <ErrorState error={error} onRetry={load} />;
  }
  if (!watchlists) {
    return <LoadingState label="Loading watchlists" />;
  }

  return (
    <div className="grid gap-6">
      <header>
        <h1 className="text-lg font-semibold">Watchlist</h1>
      </header>
      <form className="surface flex flex-wrap items-end gap-3 px-4 py-4" onSubmit={onCreate}>
        <label className="grid min-w-[16rem] flex-1 gap-1 text-sm">
          Name
          <input className="field" value={name} onChange={(event) => setName(event.target.value)} maxLength={120} />
        </label>
        <button className="button-primary" type="submit" disabled={pending}>{pending ? "Creating" : "Create watchlist"}</button>
        {formError ? <p className="w-full text-sm text-negative" role="alert">{formError}</p> : null}
      </form>
      {watchlists.length === 0 ? (
        <div className="surface px-5 py-8">
          <h2 className="text-sm font-semibold">No watchlists</h2>
          <p className="mt-2 text-sm leading-6 text-muted">Create one, then search for an instrument and add it.</p>
        </div>
      ) : (
        <ul className="grid gap-3">
          {watchlists.map((watchlist) => (
            <li key={watchlist.id} className="surface flex flex-wrap items-center justify-between gap-3 px-4 py-4">
              <div>
                <Link href={`/watchlists/${watchlist.id}`} className="font-semibold text-foreground">{watchlist.name}</Link>
                <p className="mt-1 text-sm text-muted">{watchlist.itemCount} {watchlist.itemCount === 1 ? "instrument" : "instruments"}</p>
              </div>
              <div className="flex gap-2">
                <button className="button-secondary" type="button" onClick={() => onRename(watchlist)}>Rename</button>
                <button className="button-secondary" type="button" onClick={() => onDelete(watchlist)}>Delete</button>
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
