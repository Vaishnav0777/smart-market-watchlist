package com.smartwatch.assistant;

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
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AssistantIntegrationTest extends PostgresIntegrationTest {

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
    void ownerReceivesAGroundedRelianceAnswer() throws Exception {
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

        mockMvc.perform(post("/api/v1/assistant/questions")
                        .header("Authorization", bearer(ada))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"Which investment has performed best?","portfolioId":"%s"}
                                """.formatted(portfolio.getId())))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.refused").value(false))
                .andExpect(jsonPath("$.sources[0]").value("portfolio_analytics"))
                .andExpect(jsonPath("$.answer", containsString("RELIANCE")))
                .andExpect(jsonPath("$.answer", containsString("25.00%")))
                .andExpect(jsonPath("$.answer", containsString("end of day")))
                .andExpect(jsonPath("$.answer", not(containsString("The quote is real-time."))));

        mockMvc.perform(post("/api/v1/assistant/questions")
                        .header("Authorization", bearer(grace))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"What is my total P&L?","portfolioId":"%s"}
                                """.formatted(portfolio.getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Portfolio not found"));

        mockMvc.perform(post("/api/v1/assistant/questions")
                        .header("Authorization", bearer(ada))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"What is my total P&L?","portfolioId":"%s"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Portfolio not found"));

        mockMvc.perform(post("/api/v1/assistant/questions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"What is my total P&L?\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/assistant/questions")
                        .header("Authorization", bearer(ada))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"" + "a".repeat(501) + "\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/assistant/questions")
                        .header("Authorization", bearer(grace))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"What is my current portfolio value?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("You do not have a portfolio yet."))
                .andExpect(jsonPath("$.answer", not(containsString("RELIANCE"))));
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
