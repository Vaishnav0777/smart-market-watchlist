package com.smartwatch.watchlist.repository;

import com.smartwatch.watchlist.entity.WatchlistCheckItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface WatchlistCheckItemRepository extends JpaRepository<WatchlistCheckItem, UUID> {

    @Query("""
            select item from WatchlistCheckItem item
            join fetch item.instrument
            left join fetch item.observation
            where item.check.id = :checkId
            """)
    List<WatchlistCheckItem> findDetailedByCheckId(@Param("checkId") UUID checkId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            delete from WatchlistCheckItem item
            where item.check.id in (
                select savedCheck.id from WatchlistCheck savedCheck
                where savedCheck.watchlist.id = :watchlistId
            )
            """)
    void deleteByWatchlistId(@Param("watchlistId") UUID watchlistId);
}
