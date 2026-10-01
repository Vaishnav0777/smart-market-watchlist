package com.smartwatch.marketdata.model;

/**
 * How current the provider says a quote is. This is not a forecast.
 */
public enum MarketDataQuality {
    REAL_TIME,
    DELAYED,
    END_OF_DAY,
    STALE,
    UNKNOWN
}
