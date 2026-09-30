package com.smartwatch.marketdata.repository;

import com.smartwatch.marketdata.entity.Instrument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface InstrumentRepository extends JpaRepository<Instrument, UUID> {

    Optional<Instrument> findByExchangeAndSymbol(String exchange, String symbol);
}
