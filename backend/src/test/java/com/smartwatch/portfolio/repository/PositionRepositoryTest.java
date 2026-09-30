package com.smartwatch.portfolio.repository;

import com.smartwatch.marketdata.entity.Instrument;
import com.smartwatch.marketdata.entity.InstrumentType;
import com.smartwatch.marketdata.repository.InstrumentRepository;
import com.smartwatch.portfolio.entity.Portfolio;
import com.smartwatch.portfolio.entity.Position;
import com.smartwatch.support.PostgresIntegrationTest;
import com.smartwatch.user.entity.User;
import com.smartwatch.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class PositionRepositoryTest extends PostgresIntegrationTest {

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
    void rejectsASecondPositionForTheSameInstrument() {
        User user = userRepository.saveAndFlush(new User("ada@example.com", "Ada"));
        Instrument reliance = instrumentRepository.saveAndFlush(equity("NSE", "RELIANCE"));
        Portfolio portfolio = portfolioRepository.saveAndFlush(new Portfolio(user, "Long Term Portfolio"));

        positionRepository.saveAndFlush(position(portfolio, reliance, "10.0000", "2500.0000"));

        assertThat(positionRepository.findByPortfolioId(portfolio.getId())).hasSize(1);
        assertThatThrownBy(() -> positionRepository.saveAndFlush(position(portfolio, reliance, "5.0000", "2400.0000")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void allowsTheSameInstrumentInDifferentPortfolios() {
        User user = userRepository.saveAndFlush(new User("ada@example.com", "Ada"));
        Instrument reliance = instrumentRepository.saveAndFlush(equity("NSE", "RELIANCE"));
        Portfolio longTerm = portfolioRepository.saveAndFlush(new Portfolio(user, "Long Term Portfolio"));
        Portfolio paper = portfolioRepository.saveAndFlush(new Portfolio(user, "Paper Trading"));

        positionRepository.saveAndFlush(position(longTerm, reliance, "10.0000", "2500.0000"));
        positionRepository.saveAndFlush(position(paper, reliance, "2.0000", "2490.0000"));

        assertThat(portfolioRepository.findByUserId(user.getId())).hasSize(2);
    }

    @Test
    void persistsQuantityAndAverageBuyPriceAsExactDecimals() {
        User user = userRepository.saveAndFlush(new User("ada@example.com", "Ada"));
        Instrument reliance = instrumentRepository.saveAndFlush(equity("NSE", "RELIANCE"));
        Portfolio portfolio = portfolioRepository.saveAndFlush(new Portfolio(user, "Long Term Portfolio"));
        Position saved = positionRepository.saveAndFlush(
                position(portfolio, reliance, "10.5000", "2486.7525"));

        entityManager.clear();
        Position reloaded = positionRepository.findById(saved.getId()).orElseThrow();

        assertThat(reloaded.getQuantity()).isEqualByComparingTo("10.5000");
        assertThat(reloaded.getAverageBuyPrice()).isEqualByComparingTo("2486.7525");
        assertThat(reloaded.getQuantity()).isEqualTo(new BigDecimal("10.5000"));
        assertThat(reloaded.getAverageBuyPrice()).isEqualTo(new BigDecimal("2486.7525"));
    }

    @Test
    void rejectsDuplicatePortfolioNamesForOneUser() {
        User user = userRepository.saveAndFlush(new User("ada@example.com", "Ada"));
        portfolioRepository.saveAndFlush(new Portfolio(user, "Long Term Portfolio"));

        assertThatThrownBy(() -> portfolioRepository.saveAndFlush(new Portfolio(user, "Long Term Portfolio")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static Instrument equity(String exchange, String symbol) {
        return new Instrument(symbol, symbol + " Co", exchange, "Test", InstrumentType.EQUITY);
    }

    private static Position position(Portfolio portfolio, Instrument instrument, String quantity, String price) {
        return new Position(portfolio, instrument, new BigDecimal(quantity), new BigDecimal(price));
    }
}
