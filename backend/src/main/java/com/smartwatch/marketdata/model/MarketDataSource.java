package com.smartwatch.marketdata.model;

/**
 * Who produced a quote. {@link #MOCK} is the default. {@link #UPSTOX} is the
 * V3 REST quote provider when it is enabled. {@link #OTHER} is reserved.
 */
public enum MarketDataSource {
    MOCK,
    UPSTOX,
    OTHER
}
