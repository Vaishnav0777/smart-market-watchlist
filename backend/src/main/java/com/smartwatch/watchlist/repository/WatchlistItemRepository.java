package com.smartwatch.watchlist.repository;

import com.smartwatch.watchlist.entity.WatchlistItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WatchlistItemRepository extends JpaRepository<WatchlistItem, UUID> {

    List<WatchlistItem> findByWatchlistId(UUID watchlistId);

    boolean existsByWatchlist_IdAndInstrument_Id(UUID watchlistId, UUID instrumentId);

    Optional<WatchlistItem> findByIdAndWatchlist_Id(UUID id, UUID watchlistId);

    long countByWatchlist_Id(UUID watchlistId);

    @Query("""
            select item.id from WatchlistItem item
            where item.watchlist.id = :watchlistId
            """)
    List<UUID> findIdsByWatchlistId(@Param("watchlistId") UUID watchlistId);

    @Query("""
            select item from WatchlistItem item
            join fetch item.instrument
            where item.watchlist.id = :watchlistId
            order by item.sortOrder asc, item.id asc
            """)
    List<WatchlistItem> findDetailedByWatchlistId(@Param("watchlistId") UUID watchlistId);

    @Query("select max(item.sortOrder) from WatchlistItem item where item.watchlist.id = :watchlistId")
    Integer findMaxSortOrder(@Param("watchlistId") UUID watchlistId);

    @Query("""
            select item.watchlist.id as watchlistId, count(item.id) as itemCount
            from WatchlistItem item
            where item.watchlist.user.id = :userId
            group by item.watchlist.id
            """)
    List<WatchlistItemCount> countItemsForUser(@Param("userId") UUID userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from WatchlistItem item where item.watchlist.id = :watchlistId")
    void deleteByWatchlistId(@Param("watchlistId") UUID watchlistId);

    interface WatchlistItemCount {
        UUID getWatchlistId();
        long getItemCount();
    }
}
