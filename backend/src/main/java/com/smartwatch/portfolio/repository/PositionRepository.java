package com.smartwatch.portfolio.repository;

import com.smartwatch.portfolio.entity.Position;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PositionRepository extends JpaRepository<Position, UUID> {

    List<Position> findByPortfolioId(UUID portfolioId);

    boolean existsByPortfolio_IdAndInstrument_Id(UUID portfolioId, UUID instrumentId);

    Optional<Position> findByIdAndPortfolio_Id(UUID id, UUID portfolioId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Position holding where holding.portfolio.id = :portfolioId")
    void deleteByPortfolioId(@Param("portfolioId") UUID portfolioId);
}
