"use client";

import { EmptyState, ErrorState, LoadingState, QualityBadge, SyntheticNotice } from "@/components/states";
import { ApiError } from "@/lib/api/client";
import { searchInstruments } from "@/lib/api/instruments";
import { addPosition, getPortfolio, getPortfolioAnalytics, removePosition, updatePosition } from "@/lib/api/portfolios";
import { directionMark, formatMoney, formatPercent, formatQuantity, formatSignedMoney } from "@/lib/format";
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
    <div className="grid gap-4">
      <PortfolioHeadline portfolio={portfolio} analytics={analytics} />
      <SyntheticNotice quotes={noticeQuotes(portfolio, analytics)} />
      {actionError ? <p className="text-sm text-negative" role="alert">{actionError}</p> : null}
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
      <AnalyticsSection analytics={analytics} error={analyticsError} onRetry={() => loadAnalytics(true)} />

      <form className="surface grid gap-2 px-3 py-3" onSubmit={onAdd}>
        <h2 className="text-sm font-semibold">Add a position</h2>
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
    </div>
  );
}

function PortfolioHeadline({ portfolio, analytics }: { portfolio: Portfolio; analytics: PortfolioAnalytics | null }) {
  const currency = analytics?.currency ?? null;
  return (
    <header className="border-b border-line pb-3">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <Link href="/portfolio" className="text-xs text-muted">Portfolio</Link>
        {analytics?.realTime ? <DataMark quality="REAL_TIME" source={null} /> : null}
      </div>
      <h1 className="text-base font-semibold">{portfolio.name}</h1>
      <p className="text-xs text-muted">{portfolio.positions.length} {portfolio.positions.length === 1 ? "holding" : "holdings"}</p>
      {analytics?.mixedCurrencies ? (
        <p className="mt-2 text-xs text-muted">This portfolio uses more than one currency. Invested amount, value, unrealized P&L, return, and allocation are not combined into one total.</p>
      ) : null}
      {!analytics?.mixedCurrencies && analytics?.returnPercent == null && (analytics?.unvaluedPositions ?? 0) > 0 ? (
        <p className="mt-2 text-xs text-muted">No quote is available for the open positions. They stay in the amount invested and are left out of value and profit.</p>
      ) : null}
      {analytics && !analytics.realTime && analytics.holdings.some((holding) => holding.currentValue != null) ? (
        <p className="mt-2 text-xs text-muted">These quotes are not all real-time.</p>
      ) : null}
      <div className="mt-3 flex flex-wrap items-end gap-x-8 gap-y-2">
        <div>
          <p className="text-2xl font-semibold tabular-nums tracking-tight">{formatMoney(analytics?.currentValue, currency)}</p>
          <p className="text-xs text-muted">Current value</p>
        </div>
        <p className="pb-1 text-sm tabular-nums">{formatMoney(analytics?.totalInvested, currency)} invested</p>
        <div>
          <p className="text-base font-semibold tabular-nums">
            <span className={moneyTone(analytics?.totalPnl ?? null)}>{formatSignedMoney(analytics?.totalPnl, currency)}</span>
            {" "}
            <span className={moneyTone(analytics?.returnPercent ?? null)}>
              {formatPercent(analytics?.returnPercent) ?? "—"} {directionMark(analytics?.returnPercent)}
            </span>
          </p>
          <p className="text-xs text-muted">Unrealized P&L</p>
        </div>
      </div>
    </header>
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
  const largest = [...analytics.instrumentAllocations].sort((left, right) => right.percentage - left.percentage || left.symbol.localeCompare(right.symbol));
  return (
    <div className="grid gap-3">
      <section className="grid gap-2 sm:grid-cols-2">
        <PerformerCard title="Best return" holding={analytics.best} />
        <PerformerCard title="Worst return" holding={analytics.worst} />
      </section>
      <PnlByHolding analytics={analytics} />
      <div className="grid gap-3 lg:grid-cols-2">
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
    </div>
  );
}

function PerformerCard({ title, holding }: { title: string; holding: HoldingAnalytics | null }) {
  return (
    <div className="surface px-3 py-2 text-sm">
      <p className="text-xs text-muted">{title}</p>
      {holding ? (
        <p className="mt-1">
          <Link href={`/instruments/${holding.instrumentId}`} className="font-semibold text-foreground">{holding.symbol}</Link>
          <span className={`ml-2 tabular-nums ${moneyTone(holding.pnl)}`}>{formatSignedMoney(holding.pnl, holding.currency)}</span>
          <span className={`ml-2 tabular-nums ${moneyTone(holding.returnPercent)}`}>{formatPercent(holding.returnPercent) ?? "—"} {directionMark(holding.returnPercent)}</span>
        </p>
      ) : (
        <p className="mt-1 text-muted">No valued holding to compare.</p>
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
    <div className="surface px-3 py-3">
      <h3 className="text-sm font-semibold">{title}</h3>
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
              <span className="chart-track block h-1 overflow-hidden" aria-hidden="true">
                <span className="chart-fill block h-full" style={{ width: `${barWidth(row.percentage)}%` }} />
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
      <div className="surface px-3 py-3">
        <h3 className="text-sm font-semibold">P&L by holding</h3>
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
        <h3 className="text-sm font-semibold">P&L by holding</h3>
      {ranked.length === 0 ? (
        <p className="mt-2 text-sm text-muted">No holdings to compare.</p>
      ) : (
        <ul className="mt-3 grid gap-3">
          {ranked.map((holding) => (
            <li key={holding.instrumentId} className="grid gap-1 text-sm">
              <div className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1">
                <span className="text-foreground">{holding.symbol}</span>
                <span className={`tabular-nums ${moneyTone(holding.pnl)}`}>
                  {directionMark(holding.pnl)} {formatSignedMoney(holding.pnl, holding.currency)}
                  <span className="text-muted"> · {formatPercent(holding.returnPercent) ?? "Return unavailable"}</span>
                </span>
              </div>
              {holding.pnl == null ? (
                <p className="text-xs text-muted">No quote, so this P&L is not shown as a bar.</p>
              ) : (
                <span className="chart-track relative block h-2" aria-hidden="true">
                  <span className="absolute top-0 left-1/2 h-full w-px bg-foreground/25" />
                  <span
                    className={`absolute top-0 h-full ${holding.pnl < 0 ? "bg-negative" : holding.pnl > 0 ? "bg-positive" : "bg-muted"}`}
                    style={holding.pnl < 0
                      ? { right: "50%", width: `${pnlWidth(holding.pnl, scale) / 2}%` }
                      : { left: "50%", width: `${pnlWidth(holding.pnl, scale) / 2}%` }}
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

function shareLabel(value: number | null): string {
  if (value == null) {
    return "—";
  }
  const formatted = formatPercent(value);
  if (!formatted) {
    return "—";
  }
  return formatted.startsWith("+") ? formatted.slice(1) : formatted;
}

function allocationFor(position: Position, analytics: PortfolioAnalytics | null): number | null {
  return analytics?.instrumentAllocations.find((slice) => slice.instrumentId === position.instrument.id)?.percentage ?? null;
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
      <h2 className="text-sm font-semibold">Holdings</h2>
      <div className="grid gap-3 md:hidden">
        {portfolio.positions.map((position) => (
          <HoldingCard
            key={position.id}
            position={position}
            holding={matchHolding(position, analytics)}
            allocation={allocationFor(position, analytics)}
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
      <div className="surface hidden overflow-x-auto md:block">
        <table className="market-table min-w-[64rem]">
          <thead>
            <tr>
              <th>Holding</th>
              <th className="num">Qty</th>
              <th className="num">Avg. price</th>
              <th className="num">Current price</th>
              <th className="num">Current value</th>
              <th className="num">P&L</th>
              <th className="num">Return</th>
              <th className="num">Allocation</th>
              <th>Quality</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {portfolio.positions.map((position) => (
              <HoldingRow
                key={position.id}
                position={position}
                holding={matchHolding(position, analytics)}
                allocation={allocationFor(position, analytics)}
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

function HoldingCard(props: HoldingEditors & { position: Position; holding: HoldingAnalytics | null; allocation: number | null; figuresReady: boolean }) {
  const { position, holding, allocation, figuresReady } = props;
  return (
    <article className="surface grid gap-3 px-4 py-4 text-sm">
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div>
          <Link href={`/instruments/${position.instrument.id}`} className="font-semibold text-foreground">{position.instrument.symbol}</Link>
          <p className="text-xs text-muted">{position.instrument.displayName}</p>
        </div>
        {figuresReady ? <DataMark quality={holding?.quality ?? null} source={holding?.source ?? null} /> : <span className="text-xs text-muted">—</span>}
      </div>
      <dl className="grid grid-cols-2 gap-3">
        <FigureTerm label="Quantity" value={props.editing ? null : formatQuantity(position.quantity)} />
        <FigureTerm label="Average price" value={formatMoney(position.averageBuyPrice, holding?.currency ?? position.quote?.currency)} />
        <FigureTerm label="Last price" value={formatMoney(position.quote?.price, position.quote?.currency)} />
        <FigureTerm label="Value" value={formatMoney(holding?.currentValue, holding?.currency)} />
        <FigureTerm label="P&L" value={`${directionMark(holding?.pnl)} ${formatSignedMoney(holding?.pnl, holding?.currency)}`.trim()} tone={moneyTone(holding?.pnl ?? null)} />
        <FigureTerm label="Return" value={formatPercent(holding?.returnPercent ?? null) ?? "—"} tone={moneyTone(holding?.returnPercent ?? null)} />
        <FigureTerm label="Allocation" value={shareLabel(allocation)} />
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
  allocation,
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
}: HoldingEditors & { position: Position; holding: HoldingAnalytics | null; allocation: number | null; figuresReady: boolean }) {
  return (
    <tr>
      <td>
        <Link href={`/instruments/${position.instrument.id}`} className="font-semibold text-foreground">{position.instrument.symbol}</Link>
        <p className="text-xs text-muted">{position.instrument.displayName}</p>
      </td>
      <td className="num">
        {editing ? <input className="field" value={editQuantity} onChange={(event) => onEditQuantity(event.target.value)} /> : formatQuantity(position.quantity)}
      </td>
      <td className="num">
        {editing ? <input className="field" value={editPrice} onChange={(event) => onEditPrice(event.target.value)} /> : formatMoney(position.averageBuyPrice, holding?.currency ?? position.quote?.currency)}
      </td>
      <td className="num">{formatMoney(position.quote?.price, position.quote?.currency)}</td>
      <td className="num">{formatMoney(holding?.currentValue, holding?.currency)}</td>
      <td className={`num ${moneyTone(holding?.pnl ?? null)}`}>{directionMark(holding?.pnl)} {formatSignedMoney(holding?.pnl, holding?.currency)}</td>
      <td className={`num ${moneyTone(holding?.returnPercent ?? null)}`}>{formatPercent(holding?.returnPercent ?? null) ?? "—"}</td>
      <td className="num">{shareLabel(allocation)}</td>
      <td>{figuresReady ? <DataMark quality={holding?.quality ?? null} source={holding?.source ?? null} /> : <span className="text-xs text-muted">—</span>}</td>
      <td>
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
  return (
    <span className="flex flex-wrap items-center gap-2">
      {quality ? <QualityBadge quality={quality} /> : null}
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
