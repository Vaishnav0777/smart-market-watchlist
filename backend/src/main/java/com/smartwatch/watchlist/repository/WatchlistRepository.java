package com.smartwatch.watchlist.repository;

import com.smartwatch.watchlist.entity.Watchlist;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WatchlistRepository extends JpaRepository<Watchlist, UUID> {

    List<Watchlist> findByUserId(UUID userId);

    List<Watchlist> findByUser_IdOrderByCreatedAtAscIdAsc(UUID userId);

    Optional<Watchlist> findByIdAndUser_Id(UUID id, UUID userId);

    boolean existsByUser_IdAndName(UUID userId, String name);
}
