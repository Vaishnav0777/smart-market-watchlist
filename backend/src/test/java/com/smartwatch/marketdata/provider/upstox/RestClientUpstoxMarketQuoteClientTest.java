package com.smartwatch.marketdata.provider.upstox;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.AbstractClientHttpRequest;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RestClientUpstoxMarketQuoteClientTest {

    private static final String TOKEN = "fixture-token-not-real";
    private static final String BASE_URL = "https://api.upstox.com";

    private final StubRequestFactory factory = new StubRequestFactory();
    private final RestClientUpstoxMarketQuoteClient client = new RestClientUpstoxMarketQuoteClient(
            RestClient.builder().requestFactory(factory).build(),
            BASE_URL,
            TOKEN);

    @Test
    void sendsTheBearerTokenToTheV3QuoteEndpoint() {
        factory.status = 200;
        factory.body = "{\"status\":\"success\",\"data\":{}}";

        String body = client.fetchQuotes(List.of("NSE_EQ|INE002A01018", "NSE_EQ|INE467B01029"));

        assertThat(body).contains("success");
        assertThat(factory.lastUri.toString()).startsWith(BASE_URL + "/v3/market-quote/quotes?instrument_key=");
        assertThat(factory.lastUri.toString()).contains("NSE_EQ%7CINE002A01018,NSE_EQ%7CINE467B01029");
        assertThat(factory.lastUri.toString()).doesNotContain("/v2/");
        assertThat(factory.lastHeaders.getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer " + TOKEN);
        assertThat(factory.lastHeaders.getFirst(HttpHeaders.ACCEPT)).isEqualTo("application/json");
    }

    @Test
    void mapsUnauthorizedForbiddenRateLimitAndServerErrorsWithoutTheToken() {
        assertStatus(401, MarketDataUnavailableException.Reason.UNAUTHORIZED);
        assertStatus(403, MarketDataUnavailableException.Reason.UNAUTHORIZED);
        assertStatus(429, MarketDataUnavailableException.Reason.RATE_LIMITED);
        assertStatus(503, MarketDataUnavailableException.Reason.UNAVAILABLE);
    }

    @Test
    void leavesAProgrammingFailureUnwrapped() {
        factory.bug = new IllegalStateException("mapper bug");

        assertThatThrownBy(() -> client.fetchQuotes(List.of("NSE_EQ|INE002A01018")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("mapper bug");
    }

    @Test
    void mapsANetworkFailureToUnavailable() {
        factory.networkFailure = new IOException("timed out");

        assertThatThrownBy(() -> client.fetchQuotes(List.of("NSE_EQ|INE002A01018")))
                .isInstanceOf(MarketDataUnavailableException.class)
                .satisfies(exception -> {
                    MarketDataUnavailableException failure = (MarketDataUnavailableException) exception;
                    assertThat(failure.getReason()).isEqualTo(MarketDataUnavailableException.Reason.UNAVAILABLE);
                    assertThat(failure.getMessage()).doesNotContain(TOKEN);
                });
    }

    private void assertStatus(int status, MarketDataUnavailableException.Reason reason) {
        factory.status = status;
        factory.body = "{\"status\":\"error\",\"errors\":[{\"message\":\"Invalid token " + TOKEN + "\"}]}";

        assertThatThrownBy(() -> client.fetchQuotes(List.of("NSE_EQ|INE002A01018")))
                .isInstanceOf(MarketDataUnavailableException.class)
                .satisfies(exception -> {
                    MarketDataUnavailableException failure = (MarketDataUnavailableException) exception;
                    assertThat(failure.getReason()).isEqualTo(reason);
                    assertThat(failure.getMessage()).doesNotContain(TOKEN);
                    assertThat(failure.getMessage()).doesNotContain("Bearer");
                });
    }

    private static final class StubRequestFactory implements ClientHttpRequestFactory {
        private int status = 200;
        private String body = "{}";
        private IOException networkFailure;
        private RuntimeException bug;
        private URI lastUri;
        private HttpHeaders lastHeaders = new HttpHeaders();

        @Override
        public ClientHttpRequest createRequest(URI uri, HttpMethod method) {
            lastUri = uri;
            return new StubRequest(uri, method);
        }

        private final class StubRequest extends AbstractClientHttpRequest {
            private final URI uri;
            private final HttpMethod method;

            private StubRequest(URI uri, HttpMethod method) {
                this.uri = uri;
                this.method = method;
            }

            @Override
            protected OutputStream getBodyInternal(HttpHeaders headers) {
                return OutputStream.nullOutputStream();
            }

            @Override
            protected ClientHttpResponse executeInternal(HttpHeaders headers) throws IOException {
                lastHeaders = headers;
                if (bug != null) {
                    throw bug;
                }
                if (networkFailure != null) {
                    throw networkFailure;
                }
                return new MockClientHttpResponse(body.getBytes(StandardCharsets.UTF_8), HttpStatusCode.valueOf(status));
            }

            @Override
            public HttpMethod getMethod() {
                return method;
            }

            @Override
            public URI getURI() {
                return uri;
            }
        }
    }
}
