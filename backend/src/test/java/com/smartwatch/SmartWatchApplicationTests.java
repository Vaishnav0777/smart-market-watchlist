package com.smartwatch;

import com.smartwatch.marketdata.model.Quote;
import com.smartwatch.marketdata.service.MarketDataService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SmartWatchApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MarketDataService marketDataService;

    @Test
    void healthEndpointReturnsUp() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void marketDataServiceUsesTheSyntheticProvider() {
        Quote quote = marketDataService.getQuote("TCS").orElseThrow();

        assertThat(quote.synthetic()).isTrue();
        assertThat(quote.symbol()).isEqualTo("TCS");
        assertThat(quote.companyName()).isEqualTo("Tata Consultancy Services");
    }
}
