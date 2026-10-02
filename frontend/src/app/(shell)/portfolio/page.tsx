"use client";

import { ErrorState, LoadingState } from "@/components/states";
import { ApiError } from "@/lib/api/client";
import { createPortfolio, deletePortfolio, listPortfolios, renamePortfolio } from "@/lib/api/portfolios";
import type { Portfolio } from "@/lib/types";
import Link from "next/link";
import { useEffect, useState } from "react";

export default function PortfolioPage() {
  const [portfolios, setPortfolios] = useState<Portfolio[] | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [name, setName] = useState("");
  const [formError, setFormError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  function load() {
    setError(null);
    setPortfolios(null);
    listPortfolios().then(setPortfolios).catch(setError);
  }

  useEffect(() => {
    let cancelled = false;
    listPortfolios()
      .then((next) => {
        if (!cancelled) {
          setPortfolios(next);
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
      await createPortfolio(trimmed);
      setName("");
      load();
    } catch (caught) {
      setFormError(caught instanceof ApiError ? caught.message : "Could not create the portfolio.");
    } finally {
      setPending(false);
    }
  }

  async function onRename(portfolio: Portfolio) {
    const next = window.prompt("Portfolio name", portfolio.name);
    if (next == null) {
      return;
    }
    try {
      await renamePortfolio(portfolio.id, next.trim());
      load();
    } catch (caught) {
      setFormError(caught instanceof ApiError ? caught.message : "Could not rename the portfolio.");
    }
  }

  async function onDelete(portfolio: Portfolio) {
    if (!window.confirm(`Delete ${portfolio.name}? This removes its positions.`)) {
      return;
    }
    try {
      await deletePortfolio(portfolio.id);
      load();
    } catch (caught) {
      setFormError(caught instanceof ApiError ? caught.message : "Could not delete the portfolio.");
    }
  }

  if (error) {
    return <ErrorState error={error} onRetry={load} />;
  }
  if (!portfolios) {
    return <LoadingState label="Loading portfolios" />;
  }

  const holdingCount = portfolios.reduce((sum, portfolio) => sum + portfolio.positions.length, 0);

  return (
    <div className="grid gap-6">
      <header>
        <h1 className="text-lg font-semibold">Portfolio</h1>
        <p className="mt-2 max-w-2xl text-sm leading-6 text-muted">
          A portfolio is a book of positions you record yourself. Quantity and average price are stored.
          The current price, when shown, is the latest synthetic quote.
        </p>
      </header>
      <p className="text-sm text-muted">
        {holdingCount} {holdingCount === 1 ? "position" : "positions"} across {portfolios.length}{" "}
        {portfolios.length === 1 ? "portfolio" : "portfolios"}.
      </p>
      <form className="surface flex flex-wrap items-end gap-3 px-4 py-4" onSubmit={onCreate}>
        <label className="grid min-w-[16rem] flex-1 gap-1 text-sm">
          Name
          <input className="field" value={name} onChange={(event) => setName(event.target.value)} maxLength={120} />
        </label>
        <button className="button-primary" type="submit" disabled={pending}>
          {pending ? "Creating" : "Create portfolio"}
        </button>
        {formError ? <p className="w-full text-sm text-negative" role="alert">{formError}</p> : null}
      </form>
      {portfolios.length === 0 ? (
        <div className="surface px-5 py-8">
          <h2 className="text-sm font-semibold">No portfolios</h2>
          <p className="mt-2 max-w-xl text-sm leading-6 text-muted">
            Create a portfolio, then add a position for an instrument that already exists.
          </p>
        </div>
      ) : (
        <ul className="grid gap-3">
          {portfolios.map((portfolio) => (
            <li key={portfolio.id} className="surface flex flex-wrap items-center justify-between gap-3 px-4 py-4">
              <div>
                <Link href={`/portfolio/${portfolio.id}`} className="font-semibold text-foreground">{portfolio.name}</Link>
                <p className="mt-1 text-sm text-muted">
                  {portfolio.positions.length} {portfolio.positions.length === 1 ? "position" : "positions"}
                </p>
              </div>
              <div className="flex gap-2">
                <button className="button-secondary" type="button" onClick={() => onRename(portfolio)}>Rename</button>
                <button className="button-secondary" type="button" onClick={() => onDelete(portfolio)}>Delete</button>
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
