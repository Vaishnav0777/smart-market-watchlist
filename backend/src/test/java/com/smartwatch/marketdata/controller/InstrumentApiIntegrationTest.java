package com.smartwatch.marketdata.controller;

import com.jayway.jsonpath.JsonPath;
import com.smartwatch.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class InstrumentApiIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/instruments"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/market/quotes"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void searchesTheProviderCatalogAndReusesTheSameIdentity() throws Exception {
        String token = token("ada@example.com", "Ada");

        mockMvc.perform(get("/api/v1/market/quotes").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(10))
                .andExpect(jsonPath("$[0].symbol").value("RELIANCE"))
                .andExpect(jsonPath("$[0].price").value(2500.00))
                .andExpect(jsonPath("$[0].synthetic").value(true));

        MvcResult first = mockMvc.perform(get("/api/v1/instruments").param("q", "reli")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].instrument.symbol").value("RELIANCE"))
                .andExpect(jsonPath("$[0].instrument.displayName").value("Reliance Industries"))
                .andExpect(jsonPath("$[0].instrument.exchange").value("NSE"))
                .andExpect(jsonPath("$[0].quote.synthetic").value(true))
                .andReturn();
        String instrumentId = JsonPath.read(first.getResponse().getContentAsString(), "$[0].instrument.id");

        mockMvc.perform(get("/api/v1/instruments").param("q", "RELIANCE")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].instrument.id").value(instrumentId));

        mockMvc.perform(get("/api/v1/instruments/" + instrumentId).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instrument.symbol").value("RELIANCE"))
                .andExpect(jsonPath("$.quote.currency").value("INR"))
                .andExpect(jsonPath("$.quote.volume").value(3200000));

        mockMvc.perform(get("/api/v1/instruments").param("q", "not-a-symbol")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get("/api/v1/instruments/" + UUID.randomUUID()).header("Authorization", bearer(token)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Instrument not found"));
    }

    @Test
    void reportsMembershipOnlyForTheCaller() throws Exception {
        String ada = token("ada@example.com", "Ada");
        String grace = token("grace@example.com", "Grace");
        MvcResult listing = mockMvc.perform(get("/api/v1/instruments").param("q", "TCS")
                        .header("Authorization", bearer(ada)))
                .andExpect(status().isOk())
                .andReturn();
        String instrumentId = JsonPath.read(listing.getResponse().getContentAsString(), "$[0].instrument.id");
        String watchlistId = createWatchlist(ada, "Long Term");
        mockMvc.perform(post("/api/v1/watchlists/" + watchlistId + "/items")
                        .header("Authorization", bearer(ada))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"instrumentId\":\"" + instrumentId + "\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/instruments/" + instrumentId + "/memberships")
                        .header("Authorization", bearer(ada)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].watchlistId").value(watchlistId))
                .andExpect(jsonPath("$[0].watchlistName").value("Long Term"));

        mockMvc.perform(get("/api/v1/instruments/" + instrumentId + "/memberships")
                        .header("Authorization", bearer(grace)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    private String token(String email, String displayName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"strong-password","displayName":"%s"}
                                """.formatted(email, displayName)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    private String createWatchlist(String accessToken, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/watchlists")
                        .header("Authorization", bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private static String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }
}
