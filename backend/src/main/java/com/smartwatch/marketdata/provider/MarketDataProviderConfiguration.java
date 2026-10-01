package com.smartwatch.marketdata.provider;

import com.smartwatch.marketdata.provider.upstox.JpaUpstoxInstrumentKeySource;
import com.smartwatch.marketdata.provider.upstox.RestClientUpstoxMarketQuoteClient;
import com.smartwatch.marketdata.provider.upstox.UpstoxMarketDataProvider;
import com.smartwatch.marketdata.provider.upstox.UpstoxProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Clock;

/**
 * Selects one {@link MarketDataProvider}.
 *
 * <p>Upstox is used only when {@code UPSTOX_ENABLED=true} and an access token
 * is configured. Otherwise the synthetic mock remains the provider. A blank
 * token with Upstox enabled fails startup instead of serving mock quotes.
 */
@Configuration
@EnableConfigurationProperties(UpstoxProperties.class)
public class MarketDataProviderConfiguration {

    @Bean
    @ConditionalOnProperty(name = "app.market-data.upstox.enabled", havingValue = "false", matchIfMissing = true)
    MarketDataProvider mockMarketDataProvider() {
        return new MockMarketDataProvider();
    }

    @Bean
    @ConditionalOnProperty(name = "app.market-data.upstox.enabled", havingValue = "true")
    MarketDataProvider upstoxMarketDataProvider(
            UpstoxProperties properties,
            JpaUpstoxInstrumentKeySource instrumentKeys,
            Clock clock) {
        if (!properties.tokenConfigured()) {
            throw new IllegalStateException("Upstox is enabled but UPSTOX_ACCESS_TOKEN is blank");
        }
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeout());
        requestFactory.setReadTimeout(properties.getReadTimeout());
        RestClient restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
        return new UpstoxMarketDataProvider(
                instrumentKeys,
                new RestClientUpstoxMarketQuoteClient(restClient, properties.baseUrl(), properties.accessToken()),
                clock,
                properties.getStaleAfter(),
                properties.accessToken());
    }
}
