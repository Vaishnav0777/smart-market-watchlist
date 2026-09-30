package com.smartwatch.marketdata.repository;

import com.smartwatch.marketdata.entity.Instrument;
import com.smartwatch.marketdata.entity.InstrumentType;
import com.smartwatch.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class InstrumentRepositoryTest extends PostgresIntegrationTest {

    @Autowired
    private InstrumentRepository instrumentRepository;

    @Test
    void identifiesAnInstrumentByExchangeAndSymbol() {
        Instrument nse = instrumentRepository.saveAndFlush(equity("NSE", "RELIANCE"));
        instrumentRepository.saveAndFlush(equity("BSE", "RELIANCE"));

        assertThat(instrumentRepository.findByExchangeAndSymbol("NSE", "RELIANCE")).contains(nse);
        assertThatThrownBy(() -> instrumentRepository.saveAndFlush(equity("NSE", "RELIANCE")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static Instrument equity(String exchange, String symbol) {
        return new Instrument(symbol, symbol + " Co", exchange, "Test", InstrumentType.EQUITY);
    }
}
