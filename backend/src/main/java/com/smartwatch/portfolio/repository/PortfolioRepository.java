package com.smartwatch.portfolio.repository;

import com.smartwatch.portfolio.entity.Portfolio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PortfolioRepository extends JpaRepository<Portfolio, UUID> {

    List<Portfolio> findByUserId(UUID userId);

    Optional<Portfolio> findByIdAndUser_Id(UUID id, UUID userId);

    boolean existsByUser_IdAndName(UUID userId, String name);

    @Query("""
            select distinct portfolio from Portfolio portfolio
            left join fetch portfolio.positions position
            left join fetch position.instrument
            where portfolio.user.id = :userId
            order by portfolio.name asc
            """)
    List<Portfolio> findDetailedByUserId(@Param("userId") UUID userId);

    @Query("""
            select portfolio from Portfolio portfolio
            left join fetch portfolio.positions position
            left join fetch position.instrument
            where portfolio.id = :portfolioId and portfolio.user.id = :userId
            """)
    Optional<Portfolio> findDetailedByIdAndUserId(
            @Param("portfolioId") UUID portfolioId,
            @Param("userId") UUID userId);
}
