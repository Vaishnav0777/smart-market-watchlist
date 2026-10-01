package com.smartwatch.portfolio.controller;

import com.jayway.jsonpath.JsonPath;
import com.smartwatch.marketdata.entity.Instrument;
import com.smartwatch.marketdata.entity.InstrumentType;
import com.smartwatch.marketdata.repository.InstrumentRepository;
import com.smartwatch.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PortfolioManagementIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InstrumentRepository instrumentRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void rejectsUnauthenticatedRequests() throws Exception {
        mockMvc.perform(get("/api/v1/portfolios"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/portfolios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Long Term\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/portfolios/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/v1/portfolios/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ownerCanManageAPortfolioAndAnotherUserCannot() throws Exception {
        String ada = token("ada@example.com", "Ada");
        String grace = token("grace@example.com", "Grace");
        Instrument reliance = instrumentRepository.saveAndFlush(
                new Instrument("RELIANCE", "Reliance Industries", "NSE", "Energy", InstrumentType.EQUITY));

        MvcResult created = mockMvc.perform(post("/api/v1/portfolios")
                        .header("Authorization", bearer(ada))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  Long Term  \"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Long Term"))
                .andExpect(jsonPath("$.positions").isEmpty())
                .andReturn();
        String portfolioId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(get("/api/v1/portfolios/" + portfolioId).header("Authorization", bearer(grace)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Portfolio not found"));
        mockMvc.perform(patch("/api/v1/portfolios/" + portfolioId)
                        .header("Authorization", bearer(grace))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Stolen\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/portfolios/" + portfolioId).header("Authorization", bearer(grace)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/portfolios/" + portfolioId + "/positions")
                        .header("Authorization", bearer(grace))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(positionJson(reliance.getId(), "1", "10")))
                .andExpect(status().isNotFound());

        MvcResult added = mockMvc.perform(post("/api/v1/portfolios/" + portfolioId + "/positions")
                        .header("Authorization", bearer(ada))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(positionJson(reliance.getId(), "10.5", "2000")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quantity").value(10.5))
                .andExpect(jsonPath("$.averageBuyPrice").value(2000.0000))
                .andExpect(jsonPath("$.instrument.symbol").value("RELIANCE"))
                .andExpect(jsonPath("$.quote.synthetic").value(true))
                .andReturn();
        String positionId = JsonPath.read(added.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(post("/api/v1/portfolios/" + portfolioId + "/positions")
                        .header("Authorization", bearer(ada))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(positionJson(reliance.getId(), "1", "10")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This instrument is already in the portfolio"));

        mockMvc.perform(patch("/api/v1/portfolios/" + portfolioId + "/positions/" + positionId)
                        .header("Authorization", bearer(grace))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":\"2\",\"averageBuyPrice\":\"3\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/portfolios/" + portfolioId + "/positions/" + positionId)
                        .header("Authorization", bearer(grace)))
                .andExpect(status().isNotFound());

        mockMvc.perform(patch("/api/v1/portfolios/" + portfolioId + "/positions/" + positionId)
                        .header("Authorization", bearer(ada))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":\"12\",\"averageBuyPrice\":\"2100.25\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(12.0000))
                .andExpect(jsonPath("$.averageBuyPrice").value(2100.2500));

        refreshPersistenceContext();
        mockMvc.perform(get("/api/v1/portfolios/" + portfolioId + "/positions")
                        .header("Authorization", bearer(ada)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(positionId));

        mockMvc.perform(post("/api/v1/portfolios/" + portfolioId + "/positions")
                        .header("Authorization", bearer(ada))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(positionJson(UUID.randomUUID(), "1", "10")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Instrument not found"));

        mockMvc.perform(post("/api/v1/portfolios/" + portfolioId + "/positions")
                        .header("Authorization", bearer(ada))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(positionJson(reliance.getId(), "0", "10")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(delete("/api/v1/portfolios/" + portfolioId + "/positions/" + positionId)
                        .header("Authorization", bearer(ada)))
                .andExpect(status().isNoContent());
        refreshPersistenceContext();
        mockMvc.perform(get("/api/v1/portfolios/" + portfolioId).header("Authorization", bearer(ada)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.positions").isEmpty());

        mockMvc.perform(patch("/api/v1/portfolios/" + portfolioId)
                        .header("Authorization", bearer(ada))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Core\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Core"));

        mockMvc.perform(delete("/api/v1/portfolios/" + portfolioId).header("Authorization", bearer(ada)))
                .andExpect(status().isNoContent());
        refreshPersistenceContext();
        mockMvc.perform(get("/api/v1/portfolios").header("Authorization", bearer(ada)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    private void refreshPersistenceContext() {
        entityManager.flush();
        entityManager.clear();
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

    private static String positionJson(UUID instrumentId, String quantity, String price) {
        return """
                {"instrumentId":"%s","quantity":"%s","averageBuyPrice":"%s"}
                """.formatted(instrumentId, quantity, price);
    }

    private static String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }
}
