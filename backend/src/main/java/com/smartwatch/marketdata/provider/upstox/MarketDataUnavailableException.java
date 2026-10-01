package com.smartwatch.marketdata.provider.upstox;

/**
 * The market-data provider could not return a quote.
 *
 * <p>The message is safe to show to a client. It must not contain an access
 * token, an Authorization header, or a provider response body.
 */
public class MarketDataUnavailableException extends RuntimeException {

    public enum Reason {
        NOT_CONFIGURED,
        UNAUTHORIZED,
        RATE_LIMITED,
        UNAVAILABLE,
        INVALID_RESPONSE
    }

    private final Reason reason;

    public MarketDataUnavailableException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }

    public static MarketDataUnavailableException notConfigured() {
        return new MarketDataUnavailableException(
                Reason.NOT_CONFIGURED,
                "Upstox is enabled but no access token is configured");
    }

    public static MarketDataUnavailableException unauthorized() {
        return new MarketDataUnavailableException(
                Reason.UNAUTHORIZED,
                "Market data provider rejected the request");
    }

    public static MarketDataUnavailableException rateLimited() {
        return new MarketDataUnavailableException(
                Reason.RATE_LIMITED,
                "Market data provider rate limit was reached");
    }

    public static MarketDataUnavailableException unavailable() {
        return new MarketDataUnavailableException(
                Reason.UNAVAILABLE,
                "Market data provider is unavailable");
    }

    public static MarketDataUnavailableException invalidResponse() {
        return new MarketDataUnavailableException(
                Reason.INVALID_RESPONSE,
                "Market data provider returned an invalid response");
    }

    public static MarketDataUnavailableException forHttpStatus(int status) {
        if (status == 401 || status == 403) {
            return unauthorized();
        }
        if (status == 429) {
            return rateLimited();
        }
        if (status >= 500 && status <= 599) {
            return unavailable();
        }
        return invalidResponse();
    }
}
