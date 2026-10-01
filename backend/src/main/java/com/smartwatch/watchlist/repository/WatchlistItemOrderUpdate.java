package com.smartwatch.watchlist.repository;

import com.smartwatch.watchlist.entity.WatchlistItem;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Assigns display positions 0..n-1 in one SQL update.
 */
@Component
public class WatchlistItemOrderUpdate {

    private final EntityManager entityManager;
    private final Clock clock;

    public WatchlistItemOrderUpdate(EntityManager entityManager, Clock clock) {
        this.entityManager = entityManager;
        this.clock = clock;
    }

    public int apply(UUID watchlistId, List<UUID> orderedItemIds) {
        if (orderedItemIds.isEmpty()) {
            return 0;
        }
        entityManager.flush();

        StringBuilder sql = new StringBuilder("UPDATE watchlist_items SET sort_order = CASE id ");
        for (int index = 0; index < orderedItemIds.size(); index++) {
            sql.append("WHEN ?").append(index + 1).append(" THEN ").append(index).append(' ');
        }
        int updatedAtIndex = orderedItemIds.size() + 1;
        int watchlistIndex = updatedAtIndex + 1;
        sql.append("END, updated_at = ?").append(updatedAtIndex);
        sql.append(" WHERE watchlist_id = ?").append(watchlistIndex);
        sql.append(" AND id IN (");
        for (int index = 0; index < orderedItemIds.size(); index++) {
            if (index > 0) {
                sql.append(", ");
            }
            sql.append('?').append(watchlistIndex + 1 + index);
        }
        sql.append(')');

        Query query = entityManager.createNativeQuery(sql.toString());
        for (int index = 0; index < orderedItemIds.size(); index++) {
            query.setParameter(index + 1, orderedItemIds.get(index));
        }
        query.setParameter(updatedAtIndex, Timestamp.from(clock.instant()));
        query.setParameter(watchlistIndex, watchlistId);
        for (int index = 0; index < orderedItemIds.size(); index++) {
            query.setParameter(watchlistIndex + 1 + index, orderedItemIds.get(index));
        }

        int updated = query.executeUpdate();
        evict(orderedItemIds);
        return updated;
    }

    private void evict(List<UUID> orderedItemIds) {
        for (UUID itemId : orderedItemIds) {
            WatchlistItem cached = entityManager.find(WatchlistItem.class, itemId);
            if (cached != null) {
                entityManager.detach(cached);
            }
        }
    }
}
