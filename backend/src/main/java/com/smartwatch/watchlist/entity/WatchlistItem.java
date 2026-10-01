package com.smartwatch.watchlist.entity;

import com.smartwatch.common.AuditableEntity;
import com.smartwatch.marketdata.entity.Instrument;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * A user's interest in one instrument.
 *
 * <p>This is not a holding and it does not store a price. An instrument can
 * appear only once in a given watchlist. {@code sortOrder} is display order
 * only, starting at zero.
 */
@Entity
@Table(
        name = "watchlist_items",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_watchlist_items_watchlist_instrument",
                columnNames = {"watchlist_id", "instrument_id"}),
        indexes = {
                @Index(name = "idx_watchlist_items_instrument_id", columnList = "instrument_id"),
                @Index(name = "idx_watchlist_items_watchlist_sort", columnList = "watchlist_id,sort_order")
        })
public class WatchlistItem extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "watchlist_id", nullable = false)
    private Watchlist watchlist;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instrument_id", nullable = false)
    private Instrument instrument;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected WatchlistItem() {
    }

    public WatchlistItem(Watchlist watchlist, Instrument instrument) {
        this(watchlist, instrument, 0);
    }

    public WatchlistItem(Watchlist watchlist, Instrument instrument, int sortOrder) {
        this.watchlist = watchlist;
        this.instrument = instrument;
        this.sortOrder = sortOrder;
    }

    public Watchlist getWatchlist() {
        return watchlist;
    }

    public Instrument getInstrument() {
        return instrument;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    @Override
    public String toString() {
        return "WatchlistItem{id=" + getId() + "}";
    }
}
