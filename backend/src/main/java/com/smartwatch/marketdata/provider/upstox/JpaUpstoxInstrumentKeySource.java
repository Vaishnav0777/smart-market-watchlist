package com.smartwatch.marketdata.provider.upstox;

import com.smartwatch.marketdata.entity.Instrument;
import com.smartwatch.marketdata.entity.InstrumentProviderKey;
import com.smartwatch.marketdata.model.MarketDataSource;
import com.smartwatch.marketdata.repository.InstrumentProviderKeyRepository;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

@Component
public class JpaUpstoxInstrumentKeySource implements UpstoxInstrumentKeySource {

    private final InstrumentProviderKeyRepository repository;

    public JpaUpstoxInstrumentKeySource(InstrumentProviderKeyRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<UpstoxInstrumentMapping> findForSymbols(Collection<String> symbols) {
        if (symbols == null || symbols.isEmpty()) {
            return List.of();
        }
        List<String> normalized = symbols.stream()
                .filter(symbol -> symbol != null && !symbol.isBlank())
                .map(symbol -> symbol.trim().toUpperCase(Locale.ROOT))
                .distinct()
                .toList();
        if (normalized.isEmpty()) {
            return List.of();
        }
        return repository.findByProviderAndSymbolIn(MarketDataSource.UPSTOX, normalized).stream()
                .map(JpaUpstoxInstrumentKeySource::toMapping)
                .toList();
    }

    @Override
    public List<UpstoxInstrumentMapping> findAll() {
        return repository.findByProvider(MarketDataSource.UPSTOX).stream()
                .map(JpaUpstoxInstrumentKeySource::toMapping)
                .toList();
    }

    private static UpstoxInstrumentMapping toMapping(InstrumentProviderKey key) {
        Instrument instrument = key.getInstrument();
        return new UpstoxInstrumentMapping(
                instrument.getSymbol(),
                instrument.getCompanyName(),
                instrument.getExchange(),
                instrument.getSector(),
                key.getExternalInstrumentKey());
    }
}
