package com.smartwatch.marketdata.repository;

import com.smartwatch.marketdata.entity.Instrument;
import com.smartwatch.marketdata.entity.InstrumentProviderKey;
import com.smartwatch.marketdata.entity.InstrumentType;
import com.smartwatch.marketdata.model.MarketDataSource;
import com.smartwatch.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class InstrumentProviderKeyRepositoryTest extends PostgresIntegrationTest {

    @Autowired
    private InstrumentRepository instrumentRepository;

    @Autowired
    private InstrumentProviderKeyRepository keyRepository;

    @Test
    void storesAnUpstoxKeyApartFromTheInternalSymbol() {
        Instrument reliance = instrumentRepository.saveAndFlush(
                new Instrument("RELIANCE", "Reliance Industries", "NSE", "Energy", InstrumentType.EQUITY));
        keyRepository.saveAndFlush(new InstrumentProviderKey(
                reliance, MarketDataSource.UPSTOX, "NSE_EQ|INE002A01018"));

        InstrumentProviderKey loaded = keyRepository
                .findByProviderAndSymbolIn(MarketDataSource.UPSTOX, java.util.List.of("RELIANCE"))
                .getFirst();

        assertThat(loaded.getProvider()).isEqualTo(MarketDataSource.UPSTOX);
        assertThat(loaded.getExternalInstrumentKey()).isEqualTo("NSE_EQ|INE002A01018");
        assertThat(loaded.getInstrument().getSymbol()).isEqualTo("RELIANCE");
        assertThat(loaded.getInstrument().getExchange()).isEqualTo("NSE");
    }
}
