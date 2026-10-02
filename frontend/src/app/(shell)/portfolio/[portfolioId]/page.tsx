"use client";

import { ErrorState, LoadingState, QualityBadge, SyntheticNotice } from "@/components/states";
import { ApiError } from "@/lib/api/client";
import { searchInstruments } from "@/lib/api/instruments";
import { addPosition, getPortfolio, getPortfolioAnalytics, removePosition, updatePosition } from "@/lib/api/portfolios";
import { formatMoney, formatPercent, formatQuantity } from "@/lib/format";
import type { HoldingAnalytics, InstrumentListing, Portfolio, PortfolioAnalytics, Position } from "@/lib/types";
import Link from "next/link";
import { useParams } from "next/navigation";
import { useCallback, useEffect, useState } from "react";

export default function PortfolioDetailPage() {
  const params = useParams<{ portfolioId: string }>();
  const portfolioId = params.portfolioId;
  const [portfolio, setPortfolio] = useState<Portfolio | null>(null);
  const [analytics, setAnalytics] = useState<PortfolioAnalytics | null>(null);
  const [analyticsError, setAnalyticsError] = useState<unknown>(null);
  const [error, setError] = useState<unknown>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [query, setQuery] = useState("");
  const [matches, setMatches] = useState<InstrumentListing[]>([]);
  const [selectedId, setSelectedId] = useState("");
  const [quantity, setQuantity] = useState("");
  const [price, setPrice] = useState("");
  const [pending, setPending] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editQuantity, setEditQuantity] = useState("");
  const [editPrice, setEditPrice] = useState("");

  const load = useCallback((reset: boolean) => {
    if (reset) {
      setError(null);
      setPortfolio(null);
    }
    getPortfolio(portfolioId)
      .then((next) => {
        setPortfolio(next);
        setError(null);
      })
      .catch(setError);
  }, [portfolioId]);

  const loadAnalytics = useCallback((reset: boolean) => {
    if (reset) {
      setAnalytics(null);
      setAnalyticsError(null);
    }
    getPortfolioAnalytics(portfolioId)
      .then((next) => {
        setAnalytics(next);
        setAnalyticsError(null);
      })
      .catch(setAnalyticsError);
  }, [portfolioId]);

  useEffect(() => {
    queueMicrotask(() => load(false));
  }, [load]);

  useEffect(() => {
    queueMicrotask(() => loadAnalytics(true));
  }, [loadAnalytics]);

  useEffect(() => {
    let cancelled = false;
    const handle = window.setTimeout(() => {
      searchInstruments(query)
        .then((next) => {
          if (!cancelled) {
            setMatches(next.slice(0, 6));
          }
        })
        .catch(() => {
          if (!cancelled) {
            setMatches([]);
          }
        });
    }, 250);
    return () => {
      cancelled = true;
      window.clearTimeout(handle);
    };
  }, [query]);

  async function onAdd(event: React.FormEvent) {
    event.preventDefault();
    if (!selectedId) {
      setActionError("Choose an instrument from the directory.");
      return;
    }
    if (!positiveDecimal(quantity) || !positiveDecimal(price)) {
      setActionError("Quantity and average price must be greater than zero, with at most 4 decimal places.");
      return;
    }
    setPending(true);
    setActionError(null);
    try {
      await addPosition(portfolioId, selectedId, quantity.trim(), price.trim());
      setQuantity("");
      setPrice("");
      setQuery("");
      setSelectedId("");
      load(false);
      loadAnalytics(false);
    } catch (caught) {
      setActionError(caught instanceof ApiError ? caught.message : "Could not add the position.");
    } finally {
      setPending(false);
    }
  }

  async function onSave(position: Position) {
    if (!positiveDecimal(editQuantity) || !positiveDecimal(editPrice)) {
      setActionError("Quantity and average price must be greater than zero, with at most 4 decimal places.");
      return;
    }
    try {
      await updatePosition(portfolioId, position.id, editQuantity.trim(), editPrice.trim());
      setEditingId(null);
      setActionError(null);
      load(false);
      loadAnalytics(false);
    } catch (caught) {
      setActionError(caught instanceof ApiError ? caught.message : "Could not update the position.");
    }
  }

  async function onRemove(position: Position) {
    if (!window.confirm(`Remove ${position.instrument.symbol} from this portfolio?`)) {
      return;
    }
    try {
      await removePosition(portfolioId, position.id);
      load(false);
      loadAnalytics(false);
    } catch (caught) {
      setActionError(caught instanceof ApiError ? caught.message : "Could not remove the position.");
    }
  }

  if (error) {
    return <ErrorState error={error} onRetry={() => load(true)} />;
  }
  if (!portfolio) {
    return <LoadingState label="Loading portfolio" />;
  }

  return (
    <div className="grid gap-6">
      <header>
        <Link href="/portfolio" className="text-xs tracking-[0.16em] text-brass uppercase">Portfolio</Link>
        <h1 className="mt-2 font-serif text-4xl">{portfolio.name}</h1>
        <p className="mt-2 text-sm text-muted">
          {portfolio.positions.length} {portfolio.positions.length === 1 ? "position" : "positions"}
        </p>
      </header>
      <SyntheticNotice quotes={portfolio.positions.flatMap((position) => position.quote ? [position.quote] : [])} />
      <AnalyticsSection analytics={analytics} error={analyticsError} onRetry={() => loadAnalytics(true)} />
      {actionError ? <p className="text-sm text-negative" role="alert">{actionError}</p> : null}

      <form className="surface grid gap-3 px-4 py-4" onSubmit={onAdd}>
        <h2 className="font-serif text-2xl">Add a position</h2>
        <label className="grid gap-1 text-sm">
          Instrument
          <input className="field" value={query} onChange={(event) => { setQuery(event.target.value); setSelectedId(""); }} placeholder="Search symbol or company" />
        </label>
        {matches.length > 0 ? (
          <ul className="grid gap-1">
            {matches.map((listing) => (
              <li key={listing.instrument.id}>
                <button
                  className={`button-secondary ${selectedId === listing.instrument.id ? "bg-surface" : ""}`}
                  type="button"
                  onClick={() => {
                    setSelectedId(listing.instrument.id);
                    setQuery(`${listing.instrument.symbol} · ${listing.instrument.displayName}`);
                  }}
                >
                  {listing.instrument.symbol} · {listing.instrument.exchange}
                </button>
              </li>
            ))}
          </ul>
        ) : null}
        <div className="grid gap-3 sm:grid-cols-2">
          <label className="grid gap-1 text-sm">
            Quantity
            <input className="field" inputMode="decimal" value={quantity} onChange={(event) => setQuantity(event.target.value)} />
          </label>
          <label className="grid gap-1 text-sm">
            Average price
            <input className="field" inputMode="decimal" value={price} onChange={(event) => setPrice(event.target.value)} />
          </label>
        </div>
        <button className="button-primary w-fit" type="submit" disabled={pending}>{pending ? "Adding" : "Add position"}</button>
      </form>

      {portfolio.positions.length === 0 ? (
        <div className="surface px-5 py-8">
          <h2 className="font-serif text-2xl">No positions</h2>
          <p className="mt-2 text-sm text-muted">Add an instrument that already exists in the directory.</p>
        </div>
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
                <th className="px-3 py-2 font-medium">Actions</th>
              </tr>
            </thead>
            <tbody>
              {portfolio.positions.map((position) => (
                <PositionRow
                  key={position.id}
                  position={position}
                  editing={editingId === position.id}
                  editQuantity={editQuantity}
                  editPrice={editPrice}
                  onEditQuantity={setEditQuantity}
                  onEditPrice={setEditPrice}
                  onStartEdit={() => {
                    setEditingId(position.id);
                    setEditQuantity(String(position.quantity));
                    setEditPrice(String(position.averageBuyPrice));
                  }}
                  onCancel={() => setEditingId(null)}
                  onSave={() => onSave(position)}
                  onRemove={() => onRemove(position)}
                />
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

function AnalyticsSection({
  analytics,
  error,
  onRetry,
}: {
  analytics: PortfolioAnalytics | null;
  error: unknown;
  onRetry: () => void;
}) {
  return (
    <section className="grid gap-3">
      <div className="flex flex-wrap items-center gap-3">
        <h2 className="font-serif text-2xl">Portfolio analytics</h2>
        {analytics?.realTime ? <QualityBadge quality="REAL_TIME" /> : null}
      </div>
      {!analytics && error ? <ErrorState error={error} onRetry={onRetry} /> : null}
      {!analytics && !error ? <LoadingState label="Loading analytics" /> : null}
      {error && analytics ? <p className="text-sm text-negative" role="alert">Analytics could not be refreshed.</p> : null}
      {analytics ? <AnalyticsSummary analytics={analytics} /> : null}
    </section>
  );
}

function AnalyticsSummary({ analytics }: { analytics: PortfolioAnalytics }) {
  const currency = analytics.currency;
  const noQuote = !analytics.mixedCurrencies && analytics.returnPercent == null && analytics.unvaluedPositions > 0;
  return (
    <div className="grid gap-4">
      {analytics.mixedCurrencies ? (
        <p className="text-sm leading-6 text-muted">
          This portfolio uses more than one currency. Invested amount, value, profit and loss, return, and allocation are not combined.
        </p>
      ) : null}
      {noQuote ? (
        <p className="text-sm leading-6 text-muted">
          No quote is available for the open positions. They stay in the amount invested and are left out of value and profit.
        </p>
      ) : null}
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <AnalyticsFigure label="Total invested" value={formatMoney(analytics.totalInvested, currency)} />
        <AnalyticsFigure label="Current value" value={formatMoney(analytics.currentValue, currency)} />
        <AnalyticsFigure label="Total P&L" value={formatMoney(analytics.totalPnl, currency)} tone={moneyTone(analytics.totalPnl)} />
        <AnalyticsFigure label="Return" value={formatPercent(analytics.returnPercent) ?? "—"} tone={moneyTone(analytics.returnPercent)} />
        <AnalyticsFigure label="Winners" value={String(analytics.winners)} />
        <AnalyticsFigure label="Losers" value={String(analytics.losers)} />
        <AnalyticsFigure label="Unvalued positions" value={String(analytics.unvaluedPositions)} />
      </div>
      <div className="grid gap-3 sm:grid-cols-2">
        <PerformerCard title="Best performer" holding={analytics.best} />
        <PerformerCard title="Worst performer" holding={analytics.worst} />
      </div>
      <AllocationList
        title="Sector allocation"
        empty={analytics.mixedCurrencies ? "Allocation is not combined across currencies." : "No positions to allocate."}
        rows={analytics.sectorAllocations.map((slice) => ({
          key: slice.sector,
          label: slice.sector,
          invested: slice.invested,
          percentage: slice.percentage,
        }))}
        currency={currency}
      />
      <AllocationList
        title="Instrument allocation"
        empty={analytics.mixedCurrencies ? "Allocation is not combined across currencies." : "No positions to allocate."}
        rows={analytics.instrumentAllocations.map((slice) => ({
          key: slice.instrumentId,
          label: `${slice.symbol} · ${slice.exchange}`,
          invested: slice.invested,
          percentage: slice.percentage,
        }))}
        currency={currency}
      />
    </div>
  );
}

function AnalyticsFigure({ label, value, tone }: { label: string; value: string; tone?: string }) {
  return (
    <div className="surface px-4 py-4">
      <p className="text-xs tracking-wide text-muted uppercase">{label}</p>
      <p className={`mt-2 font-serif text-2xl tabular-nums ${tone ?? "text-foreground"}`}>{value}</p>
    </div>
  );
}

function PerformerCard({ title, holding }: { title: string; holding: HoldingAnalytics | null }) {
  return (
    <div className="surface px-4 py-4">
      <h3 className="font-serif text-xl">{title}</h3>
      {holding ? (
        <div className="mt-2 flex flex-wrap items-baseline justify-between gap-2">
          <p>
            <Link href={`/instruments/${holding.instrumentId}`} className="text-foreground">{holding.symbol}</Link>
            <span className="text-sm text-muted"> · {holding.exchange}</span>
          </p>
          <p className={`tabular-nums ${moneyTone(holding.returnPercent)}`}>
            {formatPercent(holding.returnPercent) ?? "—"}
          </p>
          {holding.quality ? <QualityBadge quality={holding.quality} /> : null}
        </div>
      ) : (
        <p className="mt-2 text-sm text-muted">—</p>
      )}
    </div>
  );
}

function AllocationList({
  title,
  empty,
  rows,
  currency,
}: {
  title: string;
  empty: string;
  rows: { key: string; label: string; invested: number; percentage: number }[];
  currency: string | null;
}) {
  return (
    <div>
      <h3 className="font-serif text-xl">{title}</h3>
      {rows.length === 0 ? (
        <p className="mt-2 text-sm text-muted">{empty}</p>
      ) : (
        <ul className="mt-2 grid gap-2">
          {rows.map((row) => (
            <li key={row.key} className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1 border-t border-line py-2 text-sm">
              <span className="text-foreground">{row.label}</span>
              <span className="tabular-nums text-muted">
                {formatMoney(row.invested, currency)} · {shareLabel(row.percentage)}
              </span>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

function shareLabel(value: number): string {
  const formatted = formatPercent(value);
  if (!formatted) {
    return "—";
  }
  return formatted.startsWith("+") ? formatted.slice(1) : formatted;
}

function moneyTone(value: number | null): string {
  if (value == null || value === 0) {
    return "text-muted";
  }
  return value < 0 ? "text-negative" : "text-positive";
}

function PositionRow({
  position,
  editing,
  editQuantity,
  editPrice,
  onEditQuantity,
  onEditPrice,
  onStartEdit,
  onCancel,
  onSave,
  onRemove,
}: {
  position: Position;
  editing: boolean;
  editQuantity: string;
  editPrice: string;
  onEditQuantity: (value: string) => void;
  onEditPrice: (value: string) => void;
  onStartEdit: () => void;
  onCancel: () => void;
  onSave: () => void;
  onRemove: () => void;
}) {
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
      <td className="px-3 py-3 tabular-nums">
        {editing ? <input className="field" value={editQuantity} onChange={(event) => onEditQuantity(event.target.value)} /> : formatQuantity(position.quantity)}
      </td>
      <td className="px-3 py-3 tabular-nums">
        {editing ? <input className="field" value={editPrice} onChange={(event) => onEditPrice(event.target.value)} /> : formatMoney(position.averageBuyPrice, currency)}
      </td>
      <td className="px-3 py-3 tabular-nums">
        {position.quote ? formatMoney(position.quote.price, currency) : "No quote"}
        {position.quote ? <p className="mt-1"><QualityBadge quality={position.quote.quality} /></p> : null}
      </td>
      <td className="px-3 py-3 tabular-nums">{formatMoney(invested, currency)}</td>
      <td className="px-3 py-3 tabular-nums">{formatMoney(current, currency)}</td>
      <td className={`px-3 py-3 tabular-nums ${tone}`}>
        {pnl == null ? "—" : `${formatMoney(pnl, currency)}${formatPercent(percent) ? ` (${formatPercent(percent)})` : ""}`}
      </td>
      <td className="px-3 py-3">
        <div className="flex gap-2">
          {editing ? (
            <>
              <button className="button-primary" type="button" onClick={onSave}>Save</button>
              <button className="button-secondary" type="button" onClick={onCancel}>Cancel</button>
            </>
          ) : (
            <>
              <button className="button-secondary" type="button" onClick={onStartEdit}>Edit</button>
              <button className="button-secondary" type="button" onClick={onRemove}>Remove</button>
            </>
          )}
        </div>
      </td>
    </tr>
  );
}

function positiveDecimal(value: string): boolean {
  if (!/^\d+(\.\d{1,4})?$/.test(value.trim())) {
    return false;
  }
  return Number(value) > 0;
}
