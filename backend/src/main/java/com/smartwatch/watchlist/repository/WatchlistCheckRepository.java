package com.smartwatch.watchlist.repository;

import com.smartwatch.watchlist.entity.WatchlistCheck;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface WatchlistCheckRepository extends JpaRepository<WatchlistCheck, UUID> {

    Optional<WatchlistCheck> findByIdAndWatchlist_Id(UUID id, UUID watchlistId);

    Optional<WatchlistCheck> findFirstByWatchlist_IdOrderByCheckedAtDescIdDesc(UUID watchlistId);

    long countByWatchlist_Id(UUID watchlistId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from WatchlistCheck savedCheck where savedCheck.watchlist.id = :watchlistId")
    void deleteByWatchlistId(@Param("watchlistId") UUID watchlistId);
}
