-- Display order for watchlist items.
-- Flyway owns this change. V1 and V2 are left untouched.
-- sort_order is a display position only. It is not a price, a rank, or a market signal.
-- There is no unique constraint on (watchlist_id, sort_order): PostgreSQL checks
-- unique indexes row by row, which would reject a single UPDATE that reassigns every position.
-- Existing rows, if any, keep a stable order from created_at. No demo data is inserted.

ALTER TABLE watchlist_items
    ADD COLUMN sort_order INTEGER;

WITH ranked AS (
    SELECT id,
           (ROW_NUMBER() OVER (PARTITION BY watchlist_id ORDER BY created_at, id) - 1)::INTEGER AS sort_order
    FROM watchlist_items
)
UPDATE watchlist_items AS item
SET sort_order = ranked.sort_order
FROM ranked
WHERE item.id = ranked.id;

ALTER TABLE watchlist_items
    ALTER COLUMN sort_order SET NOT NULL;

CREATE INDEX idx_watchlist_items_watchlist_sort ON watchlist_items (watchlist_id, sort_order);
