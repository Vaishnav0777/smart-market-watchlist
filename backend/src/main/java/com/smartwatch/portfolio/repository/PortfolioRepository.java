package com.smartwatch.portfolio.repository;

import com.smartwatch.portfolio.entity.Portfolio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PortfolioRepository extends JpaRepository<Portfolio, UUID> {

    List<Portfolio> findByUserId(UUID userId);

    @Query("""
            select distinct portfolio from Portfolio portfolio
            left join fetch portfolio.positions position
            left join fetch position.instrument
            where portfolio.user.id = :userId
            order by portfolio.name asc
            """)
    List<Portfolio> findDetailedByUserId(@Param("userId") UUID userId);
}
