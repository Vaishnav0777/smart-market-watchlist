package com.smartwatch.watchlist.entity;

import com.smartwatch.marketdata.entity.Instrument;
import com.smartwatch.marketdata.entity.MarketObservation;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.Hibernate;

import java.util.UUID;

/**
 * The instrument membership captured at a check, plus the observation
 * recorded then, when the provider had a quote.
 */
@Entity
@Table(
        name = "watchlist_check_items",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_watchlist_check_items_check_instrument",
                columnNames = {"check_id", "instrument_id"}))
public class WatchlistCheckItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "check_id", nullable = false)
    private WatchlistCheck check;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instrument_id", nullable = false)
    private Instrument instrument;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "observation_id")
    private MarketObservation observation;

    protected WatchlistCheckItem() {
    }

    public WatchlistCheckItem(WatchlistCheck check, Instrument instrument, MarketObservation observation) {
        this.check = check;
        this.instrument = instrument;
        this.observation = observation;
    }

    public UUID getId() {
        return id;
    }

    public WatchlistCheck getCheck() {
        return check;
    }

    public Instrument getInstrument() {
        return instrument;
    }

    public MarketObservation getObservation() {
        return observation;
    }

    @Override
    public final boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) {
            return false;
        }
        WatchlistCheckItem that = (WatchlistCheckItem) other;
        return id != null && id.equals(that.id);
    }

    @Override
    public final int hashCode() {
        return Hibernate.getClass(this).hashCode();
    }

    @Override
    public String toString() {
        return "WatchlistCheckItem{id=" + id + "}";
    }
}
