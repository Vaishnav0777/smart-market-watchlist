package com.smartwatch.marketdata.provider.upstox;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Upstox V3 quote settings. The access token is read from the environment
 * and is not logged by this class.
 */
@ConfigurationProperties(prefix = "app.market-data.upstox")
public class UpstoxProperties {

    /**
     * Age of the Upstox snapshot {@code timestamp}, compared with the server
     * clock, after which a quote is stale. This is not the age of the last trade.
     */
    public static final Duration DEFAULT_STALE_AFTER = Duration.ofMinutes(15);

    private boolean enabled = false;

    private String baseUrl = "https://api.upstox.com";

    private String accessToken = "";

    private Duration staleAfter = DEFAULT_STALE_AFTER;

    private Duration connectTimeout = Duration.ofSeconds(5);

    private Duration readTimeout = Duration.ofSeconds(10);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public Duration getStaleAfter() {
        return staleAfter;
    }

    public void setStaleAfter(Duration staleAfter) {
        this.staleAfter = staleAfter;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }

    public String accessToken() {
        return accessToken == null ? "" : accessToken.trim();
    }

    public String baseUrl() {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "https://api.upstox.com";
        }
        String trimmed = baseUrl.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    public boolean tokenConfigured() {
        return !accessToken().isBlank();
    }
}
