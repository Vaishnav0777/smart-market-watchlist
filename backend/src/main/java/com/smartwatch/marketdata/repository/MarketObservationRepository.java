package com.smartwatch.marketdata.repository;

import com.smartwatch.marketdata.entity.MarketObservation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface MarketObservationRepository extends JpaRepository<MarketObservation, UUID> {

    List<MarketObservation> findByInstrument_IdInAndObservedAtLessThanEqualOrderByObservedAtDescIdDesc(
            Collection<UUID> instrumentIds,
            Instant observedAt);
}
