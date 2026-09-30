package com.smartwatch.portfolio.repository;

import com.smartwatch.portfolio.entity.Position;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PositionRepository extends JpaRepository<Position, UUID> {

    List<Position> findByPortfolioId(UUID portfolioId);
}
