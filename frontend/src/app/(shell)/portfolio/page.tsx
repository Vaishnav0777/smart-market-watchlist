"use client";

import { ErrorState, LoadingState, SyntheticNotice } from "@/components/states";
import { listPortfolios } from "@/lib/api/portfolios";
import { formatMoney, formatPercent, formatQuantity } from "@/lib/format";
import type { Portfolio, Position } from "@/lib/types";
import Link from "next/link";
import { useEffect, useState } from "react";

export default function PortfolioPage() {
  const [portfolios, setPortfolios] = useState<Portfolio[] | null>(null);
  const [error, setError] = useState<unknown>(null);

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
        <p className="text-xs font-medium tracking-[0.18em] text-brass uppercase">Portfolio</p>
        <h1 className="mt-2 font-serif text-4xl">Holdings</h1>
        <p className="mt-2 max-w-2xl text-sm leading-6 text-muted">
          Quantity and average price are stored on each position. Current price comes from the latest quote.
          Invested value, current value, and return are calculated here from those figures. They are not a forecast.
        </p>
      </header>
      <SyntheticNotice />
      <p className="text-sm text-muted">{holdingCount} {holdingCount === 1 ? "position" : "positions"} across {portfolios.length} {portfolios.length === 1 ? "portfolio" : "portfolios"}.</p>
      {portfolios.length === 0 ? (
        <div className="surface px-5 py-8">
          <h2 className="font-serif text-2xl">No portfolios</h2>
          <p className="mt-2 max-w-xl text-sm leading-6 text-muted">
            This account has no stored portfolios. This screen reads existing holdings and does not create them.
          </p>
        </div>
      ) : portfolios.map((portfolio) => <PortfolioCard key={portfolio.id} portfolio={portfolio} />)}
    </div>
  );
}

function PortfolioCard({ portfolio }: { portfolio: Portfolio }) {
  return (
    <section className="grid gap-3">
      <h2 className="font-serif text-2xl">{portfolio.name}</h2>
      {portfolio.positions.length === 0 ? (
        <p className="text-sm text-muted">This portfolio has no positions.</p>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full min-w-[52rem] text-left text-sm">
            <thead className="text-xs tracking-wide text-muted uppercase">
              <tr>
                <th className="px-3 py-2 font-medium">Instrument</th>
                <th className="px-3 py-2 font-medium">Quantity</th>
                <th className="px-3 py-2 font-medium">Average price</th>
                <th className="px-3 py-2 font-medium">Current price</th>
                <th className="px-3 py-2 font-medium">Invested</th>
                <th className="px-3 py-2 font-medium">Current value</th>
                <th className="px-3 py-2 font-medium">Profit / loss</th>
              </tr>
            </thead>
            <tbody>
              {portfolio.positions.map((position) => <PositionRow key={position.id} position={position} />)}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}

function PositionRow({ position }: { position: Position }) {
  const currency = position.quote?.currency ?? "INR";
  const invested = position.quantity * position.averageBuyPrice;
  const current = position.quote ? position.quantity * position.quote.price : null;
  const pnl = current == null ? null : current - invested;
  const percent = pnl == null || invested === 0 ? null : (pnl / invested) * 100;
  const tone = pnl == null ? "text-muted" : pnl < 0 ? "text-negative" : "text-positive";
  return (
    <tr className="border-t border-line">
      <td className="px-3 py-3">
        <Link href={`/instruments/${position.instrument.id}`} className="text-foreground">{position.instrument.symbol}</Link>
        <p className="text-xs text-muted">{position.instrument.displayName}</p>
      </td>
      <td className="px-3 py-3 tabular-nums">{formatQuantity(position.quantity)}</td>
      <td className="px-3 py-3 tabular-nums">{formatMoney(position.averageBuyPrice, currency)}</td>
      <td className="px-3 py-3 tabular-nums">{position.quote ? formatMoney(position.quote.price, currency) : "No quote"}</td>
      <td className="px-3 py-3 tabular-nums">{formatMoney(invested, currency)}</td>
      <td className="px-3 py-3 tabular-nums">{formatMoney(current, currency)}</td>
      <td className={`px-3 py-3 tabular-nums ${tone}`}>
        {pnl == null ? "—" : `${formatMoney(pnl, currency)}${formatPercent(percent) ? ` (${formatPercent(percent)})` : ""}`}
      </td>
    </tr>
  );
}
