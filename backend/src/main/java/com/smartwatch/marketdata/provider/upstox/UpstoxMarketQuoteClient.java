package com.smartwatch.marketdata.provider.upstox;

import java.util.List;

/**
 * GET /v3/market-quote/quotes. Implementations must not put the access token
 * into exceptions or logs.
 */
public interface UpstoxMarketQuoteClient {

    String fetchQuotes(List<String> instrumentKeys);
}
