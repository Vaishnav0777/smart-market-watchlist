package com.smartwatch.portfolio.controller;

import com.jayway.jsonpath.JsonPath;
import com.smartwatch.marketdata.entity.Instrument;
import com.smartwatch.marketdata.entity.InstrumentType;
import com.smartwatch.marketdata.repository.InstrumentRepository;
import com.smartwatch.portfolio.entity.Portfolio;
import com.smartwatch.portfolio.entity.Position;
import com.smartwatch.portfolio.repository.PortfolioRepository;
import com.smartwatch.portfolio.repository.PositionRepository;
import com.smartwatch.support.PostgresIntegrationTest;
import com.smartwatch.user.entity.User;
import com.smartwatch.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PortfolioQueryIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private InstrumentRepository instrumentRepository;

    @Autowired
    private PortfolioRepository portfolioRepository;

    @Autowired
    private PositionRepository positionRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void returnsOnlyTheCallerHoldingsAndTheMatchingQuote() throws Exception {
        String ada = token("ada@example.com", "Ada");
        String grace = token("grace@example.com", "Grace");

        mockMvc.perform(get("/api/v1/portfolios").header("Authorization", bearer(grace)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        User owner = userRepository.findByEmail("ada@example.com").orElseThrow();
        Instrument reliance = instrumentRepository.saveAndFlush(
                new Instrument("RELIANCE", "Reliance Industries", "NSE", "Energy", InstrumentType.EQUITY));
        Portfolio portfolio = portfolioRepository.saveAndFlush(new Portfolio(owner, "Long Term"));
        positionRepository.saveAndFlush(new Position(
                portfolio,
                reliance,
                new BigDecimal("10.0000"),
                new BigDecimal("2000.0000")));
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get("/api/v1/portfolios").header("Authorization", bearer(ada)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Long Term"))
                .andExpect(jsonPath("$[0].positions.length()").value(1))
                .andExpect(jsonPath("$[0].positions[0].quantity").value(10.0000))
                .andExpect(jsonPath("$[0].positions[0].averageBuyPrice").value(2000.0000))
                .andExpect(jsonPath("$[0].positions[0].instrument.symbol").value("RELIANCE"))
                .andExpect(jsonPath("$[0].positions[0].quote.price").value(2500.00))
                .andExpect(jsonPath("$[0].positions[0].quote.synthetic").value(true));

        mockMvc.perform(get("/api/v1/portfolios").header("Authorization", bearer(grace)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/portfolios"))
                .andExpect(status().isUnauthorized());
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

    private static String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }
}
