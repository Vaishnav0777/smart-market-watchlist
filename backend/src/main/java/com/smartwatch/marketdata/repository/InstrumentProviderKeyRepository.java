package com.smartwatch.marketdata.repository;

import com.smartwatch.marketdata.entity.InstrumentProviderKey;
import com.smartwatch.marketdata.model.MarketDataSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface InstrumentProviderKeyRepository extends JpaRepository<InstrumentProviderKey, UUID> {

    @Query("""
            select mapping
            from InstrumentProviderKey mapping
            join fetch mapping.instrument
            where mapping.provider = :provider
            """)
    List<InstrumentProviderKey> findByProvider(@Param("provider") MarketDataSource provider);

    @Query("""
            select mapping
            from InstrumentProviderKey mapping
            join fetch mapping.instrument instrument
            where mapping.provider = :provider
              and upper(instrument.symbol) in :symbols
            """)
    List<InstrumentProviderKey> findByProviderAndSymbolIn(
            @Param("provider") MarketDataSource provider,
            @Param("symbols") Collection<String> symbols);
}
