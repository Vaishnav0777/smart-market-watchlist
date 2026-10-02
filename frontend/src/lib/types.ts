export type CurrentUser = {
  id: string;
  email: string;
  displayName: string;
  createdAt: string;
};

export type AuthResponse = {
  accessToken: string;
  expiresInSeconds: number;
  user: CurrentUser;
};

export type ApiErrorBody = {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  fieldErrors?: Record<string, string> | null;
};

export type Instrument = {
  id: string;
  symbol: string;
  displayName: string;
  exchange: string;
  sector: string;
  instrumentType: string;
};

export type MarketDataSource = "MOCK" | "UPSTOX" | "OTHER";

export type MarketDataQuality = "REAL_TIME" | "DELAYED" | "END_OF_DAY" | "STALE" | "UNKNOWN";

export type MarketQuote = {
  symbol: string;
  companyName: string;
  exchange: string;
  sector: string;
  price: number;
  previousClose: number | null;
  open: number | null;
  high: number | null;
  low: number | null;
  volume: number;
  marketTimestamp: string;
  observedAt: string | null;
  source: MarketDataSource;
  quality: MarketDataQuality;
  timestamp: string;
  currency: string;
  sessionDate: string | null;
  synthetic: boolean;
};

export type QuoteObservation = {
  symbol: string;
  exchange: string;
  price: number;
  previousClose: number | null;
  marketTimestamp: string;
  observedAt: string | null;
  source: MarketDataSource;
  quality: MarketDataQuality;
  timestamp: string;
  synthetic: boolean;
};

export type InstrumentListing = {
  instrument: Instrument;
  quote: MarketQuote;
};

export type InstrumentDetail = {
  instrument: Instrument;
  quote: MarketQuote | null;
};

export type InstrumentMembership = {
  watchlistId: string;
  watchlistName: string;
  itemId: string;
};

export type WatchlistSummary = {
  id: string;
  name: string;
  itemCount: number;
  createdAt: string;
  updatedAt: string;
};

export type WatchlistItem = {
  id: string;
  position: number;
  addedAt: string;
  instrument: Instrument;
  quote?: QuoteObservation | null;
};

export type WatchlistDetail = {
  id: string;
  name: string;
  createdAt: string;
  updatedAt: string;
  items: WatchlistItem[];
};

export type ChangeType =
  | "PRICE_MOVE"
  | "VOLUME_SPIKE"
  | "NEW_DAY_HIGH"
  | "NEW_DAY_LOW"
  | "GAP_UP"
  | "GAP_DOWN"
  | "LARGE_INTRADAY_MOVE"
  | "WATCHLIST_ADDED"
  | "WATCHLIST_REMOVED";

export type ChangeSeverity = "NOTABLE" | "HIGH";

export type DetectedChange = {
  instrumentId: string;
  symbol: string;
  exchange: string;
  type: ChangeType;
  severity: ChangeSeverity;
  currentValue: number | null;
  referenceValue: number | null;
  absoluteChange: number | null;
  changePercent: number | null;
  currency: string | null;
  detectedAt: string;
  message: string;
};

export type ChangeSummary = {
  totalChanges: number;
  instrumentsWithChanges: number;
  priceMoves: number;
  volumeSpikes: number;
  newDayHighs: number;
  newDayLows: number;
  gapUps: number;
  gapDowns: number;
  largeIntradayMoves: number;
  watchlistAdded: number;
  watchlistRemoved: number;
  highlights: string[];
};

export type WatchlistChanges = {
  watchlistId: string;
  cursor: string | null;
  baselineCheckedAt: string | null;
  firstCheck: boolean;
  changes: DetectedChange[];
  summary: ChangeSummary;
};

export type WatchlistCheck = {
  watchlistId: string;
  cursor: string;
  checkedAt: string;
};

export type Position = {
  id: string;
  quantity: number;
  averageBuyPrice: number;
  instrument: Instrument;
  quote: MarketQuote | null;
};

export type Portfolio = {
  id: string;
  name: string;
  createdAt: string;
  updatedAt: string;
  positions: Position[];
};

export type HoldingAnalytics = {
  instrumentId: string;
  symbol: string;
  exchange: string;
  sector: string;
  currency: string | null;
  invested: number;
  currentValue: number | null;
  pnl: number | null;
  returnPercent: number | null;
  quality: MarketDataQuality | null;
  source: MarketDataSource | null;
  marketTimestamp: string | null;
  observedAt: string | null;
};

export type SectorAllocation = {
  sector: string;
  currentValue: number;
  percentage: number;
};

export type InstrumentAllocation = {
  instrumentId: string;
  symbol: string;
  exchange: string;
  sector: string;
  currentValue: number;
  percentage: number;
};

export type AssistantSource = "portfolio_analytics" | "watchlist_changes" | "portfolio_summary";

export type AssistantAnswer = {
  answer: string;
  refused: boolean;
  sources: AssistantSource[];
};

export type PortfolioAnalytics = {
  portfolioId: string;
  currency: string | null;
  mixedCurrencies: boolean;
  realTime: boolean;
  totalInvested: number | null;
  currentValue: number | null;
  totalPnl: number | null;
  returnPercent: number | null;
  winners: number;
  losers: number;
  unvaluedPositions: number;
  best: HoldingAnalytics | null;
  worst: HoldingAnalytics | null;
  sectorAllocations: SectorAllocation[];
  instrumentAllocations: InstrumentAllocation[];
  holdings: HoldingAnalytics[];
};
