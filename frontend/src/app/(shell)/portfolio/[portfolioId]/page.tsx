"use client";

import { EmptyState, ErrorState, LoadingState, SyntheticNotice } from "@/components/states";
import { ApiError } from "@/lib/api/client";
import { searchInstruments } from "@/lib/api/instruments";
import { addPosition, getPortfolio, getPortfolioAnalytics, removePosition, updatePosition } from "@/lib/api/portfolios";
import { formatMoney, formatPercent, formatQuantity, qualityLabel } from "@/lib/format";
import type { HoldingAnalytics, InstrumentListing, MarketDataQuality, MarketDataSource, Portfolio, PortfolioAnalytics, Position } from "@/lib/types";
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
      <header className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <Link href="/portfolio" className="text-xs tracking-[0.16em] text-brass uppercase">Portfolio</Link>
          <h1 className="mt-2 font-serif text-4xl">{portfolio.name}</h1>
          <p className="mt-2 text-sm text-muted">
            {portfolio.positions.length} {portfolio.positions.length === 1 ? "holding" : "holdings"}
          </p>
        </div>
        {analytics?.realTime ? <DataMark quality="REAL_TIME" source={null} /> : null}
      </header>
      <SyntheticNotice quotes={noticeQuotes(portfolio, analytics)} />
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

      <HoldingsSection
        portfolio={portfolio}
        analytics={analytics}
        editingId={editingId}
        editQuantity={editQuantity}
        editPrice={editPrice}
        onEditQuantity={setEditQuantity}
        onEditPrice={setEditPrice}
        onStartEdit={(position) => {
          setEditingId(position.id);
          setEditQuantity(String(position.quantity));
          setEditPrice(String(position.averageBuyPrice));
        }}
        onCancel={() => setEditingId(null)}
        onSave={onSave}
        onRemove={onRemove}
      />
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
  const largest = [...analytics.instrumentAllocations].sort((left, right) => right.percentage - left.percentage || left.symbol.localeCompare(right.symbol));
  return (
    <div className="grid gap-6">
      {analytics.mixedCurrencies ? (
        <p className="text-sm leading-6 text-muted">
          This portfolio uses more than one currency. Invested amount, value, unrealized P&L, return, and allocation are not combined into one total.
        </p>
      ) : null}
      {noQuote ? (
        <p className="text-sm leading-6 text-muted">
          No quote is available for the open positions. They stay in the amount invested and are left out of value and profit.
        </p>
      ) : null}
      {!analytics.realTime && analytics.holdings.some((holding) => holding.currentValue != null) ? (
        <p className="text-sm leading-6 text-muted">These quotes are not all real-time.</p>
      ) : null}
      <section className="grid gap-3">
        <h2 className="font-serif text-2xl">Summary</h2>
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          <AnalyticsFigure label="Total portfolio value" value={formatMoney(analytics.currentValue, currency)} featured />
          <AnalyticsFigure label="Total invested" value={formatMoney(analytics.totalInvested, currency)} />
          <AnalyticsFigure label="Unrealized P&L" value={formatMoney(analytics.totalPnl, currency)} tone={moneyTone(analytics.totalPnl)} />
          <AnalyticsFigure label="Return" value={formatPercent(analytics.returnPercent) ?? "—"} tone={moneyTone(analytics.returnPercent)} />
        </div>
      </section>
      <section className="grid gap-3">
        <h2 className="font-serif text-2xl">Performance</h2>
        <div className="grid gap-3 sm:grid-cols-2">
          <PerformerCard title="Best-performing holding" holding={analytics.best} />
          <PerformerCard title="Worst-performing holding" holding={analytics.worst} />
        </div>
        <PnlByHolding analytics={analytics} />
      </section>
      <section className="grid gap-4">
        <h2 className="font-serif text-2xl">Allocation</h2>
        <div className="grid gap-4 lg:grid-cols-2">
          <AllocationList
            title="Holding allocation"
            empty={analytics.mixedCurrencies ? "Allocation is not combined across currencies." : "No positions to allocate."}
            rows={largest.map((slice) => ({
              key: slice.instrumentId,
              label: slice.symbol,
              detail: slice.exchange,
              invested: slice.invested,
              percentage: slice.percentage,
            }))}
            currency={currency}
          />
          <AllocationList
            title="Sector allocation"
            empty={analytics.mixedCurrencies ? "Allocation is not combined across currencies." : "No positions to allocate."}
            rows={analytics.sectorAllocations.map((slice) => ({
              key: slice.sector,
              label: slice.sector,
              detail: null,
              invested: slice.invested,
              percentage: slice.percentage,
            }))}
            currency={currency}
          />
        </div>
      </section>
    </div>
  );
}

function AnalyticsFigure({ label, value, tone, featured }: { label: string; value: string; tone?: string; featured?: boolean }) {
  return (
    <div className={`surface px-4 py-4 ${featured ? "sm:col-span-2 lg:col-span-1" : ""}`}>
      <p className="text-xs tracking-[0.14em] text-brass uppercase">{label}</p>
      <p className={`mt-2 font-serif tabular-nums ${featured ? "text-4xl" : "text-2xl"} ${tone ?? "text-foreground"}`}>{value}</p>
    </div>
  );
}

function PerformerCard({ title, holding }: { title: string; holding: HoldingAnalytics | null }) {
  return (
    <div className="surface px-4 py-4">
      <h3 className="font-serif text-xl">{title}</h3>
      {holding ? (
        <div className="mt-3 grid gap-2">
          <p>
            <Link href={`/instruments/${holding.instrumentId}`} className="text-foreground">{holding.symbol}</Link>
            <span className="text-sm text-muted"> · {holding.exchange}</span>
          </p>
          <p className={`font-serif text-2xl tabular-nums ${moneyTone(holding.pnl)}`}>
            {formatMoney(holding.pnl, holding.currency)}
          </p>
          <p className={`text-sm tabular-nums ${moneyTone(holding.returnPercent)}`}>
            {formatPercent(holding.returnPercent) ?? "Return unavailable"}
          </p>
          <DataMark quality={holding.quality} source={holding.source} />
        </div>
      ) : (
        <p className="mt-2 text-sm text-muted">No valued holding to compare.</p>
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
  rows: { key: string; label: string; detail: string | null; invested: number; percentage: number }[];
  currency: string | null;
}) {
  return (
    <div className="surface px-4 py-4">
      <h3 className="font-serif text-xl">{title}</h3>
      {rows.length === 0 ? (
        <p className="mt-2 text-sm text-muted">{empty}</p>
      ) : (
        <ul className="mt-3 grid gap-3">
          {rows.map((row) => (
            <li key={row.key} className="grid gap-1 text-sm">
              <div className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1">
                <span className="text-foreground">
                  {row.label}
                  {row.detail ? <span className="text-muted"> · {row.detail}</span> : null}
                </span>
                <span className="tabular-nums text-muted">
                  {shareLabel(row.percentage)} · {formatMoney(row.invested, currency)}
                </span>
              </div>
              <span className="block h-1.5 overflow-hidden rounded-full bg-line" aria-hidden="true">
                <span className="block h-full rounded-full bg-brass" style={{ width: `${barWidth(row.percentage)}%` }} />
              </span>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

function PnlByHolding({ analytics }: { analytics: PortfolioAnalytics }) {
  if (analytics.mixedCurrencies) {
    return (
      <div className="surface px-4 py-4">
        <h3 className="font-serif text-xl">P&L by holding</h3>
        <p className="mt-2 text-sm leading-6 text-muted">
          Holdings use more than one currency, so their P&L is not drawn on one scale.
        </p>
      </div>
    );
  }
  const ranked = [...analytics.holdings].sort((left, right) => {
    if (left.pnl == null && right.pnl == null) {
      return left.symbol.localeCompare(right.symbol);
    }
    if (left.pnl == null) {
      return 1;
    }
    if (right.pnl == null) {
      return -1;
    }
    return right.pnl - left.pnl || left.symbol.localeCompare(right.symbol);
  });
  const scale = ranked.reduce((max, holding) => holding.pnl == null ? max : Math.max(max, Math.abs(holding.pnl)), 0);
  return (
    <div className="surface px-4 py-4">
      <h3 className="font-serif text-xl">P&L by holding</h3>
      {ranked.length === 0 ? (
        <p className="mt-2 text-sm text-muted">No holdings to compare.</p>
      ) : (
        <ul className="mt-3 grid gap-3">
          {ranked.map((holding) => (
            <li key={holding.instrumentId} className="grid gap-1 text-sm">
              <div className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1">
                <span className="text-foreground">{holding.symbol}</span>
                <span className={`tabular-nums ${moneyTone(holding.pnl)}`}>
                  {formatMoney(holding.pnl, holding.currency)}
                  <span className="text-muted"> · {formatPercent(holding.returnPercent) ?? "Return unavailable"}</span>
                </span>
              </div>
              {holding.pnl == null ? (
                <p className="text-xs text-muted">No quote, so this P&L is not shown as a bar.</p>
              ) : (
                <span className="block h-1.5 overflow-hidden rounded-full bg-line" aria-hidden="true">
                  <span
                    className={`block h-full rounded-full ${holding.pnl < 0 ? "bg-negative" : "bg-positive"}`}
                    style={{ width: `${pnlWidth(holding.pnl, scale)}%` }}
                  />
                </span>
              )}
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

function barWidth(percentage: number): number {
  if (!Number.isFinite(percentage) || percentage <= 0) {
    return 0;
  }
  return Math.min(percentage, 100);
}

function pnlWidth(pnl: number, scale: number): number {
  if (scale <= 0 || !Number.isFinite(pnl)) {
    return 0;
  }
  return Math.min(100, (Math.abs(pnl) / scale) * 100);
}

function moneyTone(value: number | null): string {
  if (value == null || value === 0) {
    return "text-muted";
  }
  return value < 0 ? "text-negative" : "text-positive";
}

function HoldingsSection({
  portfolio,
  analytics,
  editingId,
  editQuantity,
  editPrice,
  onEditQuantity,
  onEditPrice,
  onStartEdit,
  onCancel,
  onSave,
  onRemove,
}: {
  portfolio: Portfolio;
  analytics: PortfolioAnalytics | null;
  editingId: string | null;
  editQuantity: string;
  editPrice: string;
  onEditQuantity: (value: string) => void;
  onEditPrice: (value: string) => void;
  onStartEdit: (position: Position) => void;
  onCancel: () => void;
  onSave: (position: Position) => void;
  onRemove: (position: Position) => void;
}) {
  if (portfolio.positions.length === 0) {
    return <EmptyState title="No holdings" body="Add an instrument that already exists in the directory." />;
  }
  return (
    <section className="grid gap-3">
      <h2 className="font-serif text-2xl">Holdings</h2>
      <div className="grid gap-3 md:hidden">
        {portfolio.positions.map((position) => (
          <HoldingCard
            key={position.id}
            position={position}
            holding={matchHolding(position, analytics)}
            figuresReady={analytics != null}
            editing={editingId === position.id}
            editQuantity={editQuantity}
            editPrice={editPrice}
            onEditQuantity={onEditQuantity}
            onEditPrice={onEditPrice}
            onStartEdit={() => onStartEdit(position)}
            onCancel={onCancel}
            onSave={() => onSave(position)}
            onRemove={() => onRemove(position)}
          />
        ))}
      </div>
      <div className="hidden overflow-x-auto md:block">
        <table className="w-full min-w-[46rem] text-left text-sm">
          <thead className="text-xs tracking-wide text-muted uppercase">
            <tr>
              <th className="px-3 py-2 font-medium">Symbol</th>
              <th className="px-3 py-2 font-medium">Quantity</th>
              <th className="px-3 py-2 font-medium">Average price</th>
              <th className="px-3 py-2 font-medium">Current value</th>
              <th className="px-3 py-2 font-medium">P&L</th>
              <th className="px-3 py-2 font-medium">Return</th>
              <th className="px-3 py-2 font-medium">Quote</th>
              <th className="px-3 py-2 font-medium">Actions</th>
            </tr>
          </thead>
          <tbody>
            {portfolio.positions.map((position) => (
              <HoldingRow
                key={position.id}
                position={position}
                holding={matchHolding(position, analytics)}
                figuresReady={analytics != null}
                editing={editingId === position.id}
                editQuantity={editQuantity}
                editPrice={editPrice}
                onEditQuantity={onEditQuantity}
                onEditPrice={onEditPrice}
                onStartEdit={() => onStartEdit(position)}
                onCancel={onCancel}
                onSave={() => onSave(position)}
                onRemove={() => onRemove(position)}
              />
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}

function HoldingCard(props: HoldingEditors & { position: Position; holding: HoldingAnalytics | null; figuresReady: boolean }) {
  const { position, holding, figuresReady } = props;
  return (
    <article className="surface grid gap-3 px-4 py-4 text-sm">
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div>
          <Link href={`/instruments/${position.instrument.id}`} className="text-foreground">{position.instrument.symbol}</Link>
          <p className="text-xs text-muted">{position.instrument.displayName}</p>
        </div>
        {figuresReady ? <DataMark quality={holding?.quality ?? null} source={holding?.source ?? null} /> : <span className="text-xs text-muted">—</span>}
      </div>
      <dl className="grid grid-cols-2 gap-3">
        <FigureTerm label="Quantity" value={props.editing ? null : formatQuantity(position.quantity)} />
        <FigureTerm label="Current value" value={formatMoney(holding?.currentValue, holding?.currency)} tone={moneyTone(holding?.pnl ?? null)} />
        <FigureTerm label="P&L" value={formatMoney(holding?.pnl, holding?.currency)} tone={moneyTone(holding?.pnl ?? null)} />
        <FigureTerm label="Return" value={formatPercent(holding?.returnPercent ?? null) ?? "—"} tone={moneyTone(holding?.returnPercent ?? null)} />
      </dl>
      <HoldingEditors
        editing={props.editing}
        editQuantity={props.editQuantity}
        editPrice={props.editPrice}
        onEditQuantity={props.onEditQuantity}
        onEditPrice={props.onEditPrice}
        onStartEdit={props.onStartEdit}
        onCancel={props.onCancel}
        onSave={props.onSave}
        onRemove={props.onRemove}
      />
    </article>
  );
}

function FigureTerm({ label, value, tone }: { label: string; value: string | null; tone?: string }) {
  return (
    <div>
      <dt className="text-xs tracking-wide text-muted uppercase">{label}</dt>
      <dd className={`mt-1 tabular-nums ${tone ?? "text-foreground"}`}>{value}</dd>
    </div>
  );
}

function HoldingRow({
  position,
  holding,
  figuresReady,
  editing,
  editQuantity,
  editPrice,
  onEditQuantity,
  onEditPrice,
  onStartEdit,
  onCancel,
  onSave,
  onRemove,
}: HoldingEditors & { position: Position; holding: HoldingAnalytics | null; figuresReady: boolean }) {
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
        {editing ? <input className="field" value={editPrice} onChange={(event) => onEditPrice(event.target.value)} /> : formatMoney(position.averageBuyPrice, holding?.currency ?? position.quote?.currency)}
      </td>
      <td className="px-3 py-3 tabular-nums">{formatMoney(holding?.currentValue, holding?.currency)}</td>
      <td className={`px-3 py-3 tabular-nums ${moneyTone(holding?.pnl ?? null)}`}>{formatMoney(holding?.pnl, holding?.currency)}</td>
      <td className={`px-3 py-3 tabular-nums ${moneyTone(holding?.returnPercent ?? null)}`}>{formatPercent(holding?.returnPercent ?? null) ?? "—"}</td>
      <td className="px-3 py-3">{figuresReady ? <DataMark quality={holding?.quality ?? null} source={holding?.source ?? null} /> : <span className="text-xs text-muted">—</span>}</td>
      <td className="px-3 py-3">
        <RowActions editing={editing} onStartEdit={onStartEdit} onCancel={onCancel} onSave={onSave} onRemove={onRemove} />
      </td>
    </tr>
  );
}

type HoldingEditors = {
  editing: boolean;
  editQuantity: string;
  editPrice: string;
  onEditQuantity: (value: string) => void;
  onEditPrice: (value: string) => void;
  onStartEdit: () => void;
  onCancel: () => void;
  onSave: () => void;
  onRemove: () => void;
};

function HoldingEditors({
  editing,
  editQuantity,
  editPrice,
  onEditQuantity,
  onEditPrice,
  onStartEdit,
  onCancel,
  onSave,
  onRemove,
}: HoldingEditors) {
  return (
    <div className="grid gap-2">
      {editing ? (
        <div className="grid gap-2 sm:grid-cols-2">
          <label className="grid gap-1 text-xs text-muted">
            Quantity
            <input className="field" value={editQuantity} onChange={(event) => onEditQuantity(event.target.value)} />
          </label>
          <label className="grid gap-1 text-xs text-muted">
            Average price
            <input className="field" value={editPrice} onChange={(event) => onEditPrice(event.target.value)} />
          </label>
        </div>
      ) : null}
      <RowActions editing={editing} onStartEdit={onStartEdit} onCancel={onCancel} onSave={onSave} onRemove={onRemove} />
    </div>
  );
}

function RowActions({
  editing,
  onStartEdit,
  onCancel,
  onSave,
  onRemove,
}: Pick<HoldingEditors, "editing" | "onStartEdit" | "onCancel" | "onSave" | "onRemove">) {
  return (
    <div className="flex flex-wrap gap-2">
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
  );
}

function matchHolding(position: Position, analytics: PortfolioAnalytics | null): HoldingAnalytics | null {
  return analytics?.holdings.find((holding) => holding.instrumentId === position.instrument.id) ?? null;
}

function noticeQuotes(portfolio: Portfolio, analytics: PortfolioAnalytics | null): { source: MarketDataSource }[] {
  if (analytics) {
    return analytics.holdings.flatMap((holding) => holding.source ? [{ source: holding.source }] : []);
  }
  return portfolio.positions.flatMap((position) => position.quote ? [{ source: position.quote.source }] : []);
}

function DataMark({ quality, source }: { quality: MarketDataQuality | null; source: MarketDataSource | null }) {
  if (!quality && !source) {
    return <span className="text-xs text-muted">No quote</span>;
  }
  const tone = quality === "REAL_TIME"
    ? "border-positive text-positive"
    : quality === "DELAYED" || quality === "END_OF_DAY"
      ? "border-brass text-brass"
      : "border-negative text-negative";
  return (
    <span className="flex flex-wrap items-center gap-2">
      {quality ? <span className={`inline-flex rounded-full border px-2 py-0.5 text-xs ${tone}`}>{qualityLabel(quality)}</span> : null}
      {source ? <span className="text-xs text-muted">{sourceLabel(source)}</span> : null}
    </span>
  );
}

function sourceLabel(source: MarketDataSource): string {
  switch (source) {
    case "MOCK":
      return "Synthetic";
    case "UPSTOX":
      return "Upstox";
    case "OTHER":
      return "Other provider";
  }
}

function positiveDecimal(value: string): boolean {
  if (!/^\d+(\.\d{1,4})?$/.test(value.trim())) {
    return false;
  }
  return Number(value) > 0;
}
