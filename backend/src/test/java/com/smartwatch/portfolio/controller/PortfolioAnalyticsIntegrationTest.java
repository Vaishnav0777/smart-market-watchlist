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
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PortfolioAnalyticsIntegrationTest extends PostgresIntegrationTest {

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
    void ownerReceivesRelianceAnalyticsAndOtherCallersDoNot() throws Exception {
        String ada = token("ada@example.com", "Ada");
        String grace = token("grace@example.com", "Grace");
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

        mockMvc.perform(get("/api/v1/portfolios/" + portfolio.getId() + "/analytics")
                        .header("Authorization", bearer(ada)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.currency").value("INR"))
                .andExpect(jsonPath("$.mixedCurrencies").value(false))
                .andExpect(jsonPath("$.realTime").value(false))
                .andExpect(jsonPath("$.totalInvested").value(20000.0000))
                .andExpect(jsonPath("$.currentValue").value(25000.0000))
                .andExpect(jsonPath("$.totalPnl").value(5000.0000))
                .andExpect(jsonPath("$.returnPercent").value(25.00))
                .andExpect(jsonPath("$.winners").value(1))
                .andExpect(jsonPath("$.losers").value(0))
                .andExpect(jsonPath("$.unvaluedPositions").value(0))
                .andExpect(jsonPath("$.best.symbol").value("RELIANCE"))
                .andExpect(jsonPath("$.best.quality").value("END_OF_DAY"))
                .andExpect(jsonPath("$.best.source").value("MOCK"))
                .andExpect(jsonPath("$.worst.symbol").value("RELIANCE"))
                .andExpect(jsonPath("$.sectorAllocations[0].sector").value("Energy"))
                .andExpect(jsonPath("$.sectorAllocations[0].currentValue").value(25000.0000))
                .andExpect(jsonPath("$.sectorAllocations[0].percentage").value(100.00))
                .andExpect(jsonPath("$.instrumentAllocations[0].symbol").value("RELIANCE"))
                .andExpect(jsonPath("$.instrumentAllocations[0].currentValue").value(25000.0000))
                .andExpect(jsonPath("$.instrumentAllocations[0].percentage").value(100.00));

        mockMvc.perform(get("/api/v1/portfolios/" + portfolio.getId() + "/analytics")
                        .header("Authorization", bearer(grace)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Portfolio not found"));

        mockMvc.perform(get("/api/v1/portfolios/" + portfolio.getId() + "/analytics"))
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
