package com.smartwatch.marketdata.provider.upstox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Calls {@code GET /v3/market-quote/quotes}. The bearer token is sent as a
 * header and is not written into the failure message.
 */
public final class RestClientUpstoxMarketQuoteClient implements UpstoxMarketQuoteClient {

    private static final Logger log = LoggerFactory.getLogger(RestClientUpstoxMarketQuoteClient.class);

    private final RestClient restClient;
    private final String baseUrl;
    private final String accessToken;

    public RestClientUpstoxMarketQuoteClient(RestClient restClient, String baseUrl, String accessToken) {
        this.restClient = restClient;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.accessToken = accessToken;
    }

    @Override
    public String fetchQuotes(List<String> instrumentKeys) {
        if (instrumentKeys == null || instrumentKeys.isEmpty()) {
            return "{\"status\":\"success\",\"data\":{}}";
        }
        String instrumentKey = instrumentKeys.stream()
                .map(key -> URLEncoder.encode(key, StandardCharsets.UTF_8).replace("+", "%20"))
                .collect(Collectors.joining(","));
        URI uri = URI.create(baseUrl + "/v3/market-quote/quotes?instrument_key=" + instrumentKey);
        try {
            return restClient.get()
                    .uri(uri)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .retrieve()
                    .onStatus(status -> status.isError(), (request, response) -> {
                        int code = response.getStatusCode().value();
                        log.warn("Upstox quote request failed with HTTP {}", code);
                        throw MarketDataUnavailableException.forHttpStatus(code);
                    })
                    .body(String.class);
        } catch (MarketDataUnavailableException exception) {
            throw exception;
        } catch (ResourceAccessException exception) {
            log.warn("Upstox quote request failed before a response");
            throw MarketDataUnavailableException.unavailable();
        } catch (RestClientResponseException exception) {
            MarketDataUnavailableException marketFailure = marketFailure(exception);
            if (marketFailure != null) {
                throw marketFailure;
            }
            int code = exception.getStatusCode().value();
            log.warn("Upstox quote request failed with HTTP {}", code);
            throw MarketDataUnavailableException.forHttpStatus(code);
        } catch (RestClientException exception) {
            MarketDataUnavailableException marketFailure = marketFailure(exception);
            if (marketFailure != null) {
                throw marketFailure;
            }
            throw exception;
        }
    }

    private static MarketDataUnavailableException marketFailure(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof MarketDataUnavailableException market) {
                return market;
            }
            current = current.getCause();
        }
        return null;
    }
}
