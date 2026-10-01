package com.smartwatch.marketdata.entity;

import com.smartwatch.marketdata.model.MarketDataQuality;
import com.smartwatch.marketdata.model.MarketDataSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.Hibernate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One recorded market observation for an instrument.
 *
 * <p>The instrument row stays the identity. This row stores the figures
 * needed to compare a later quote. It is written when a user checks a
 * watchlist, not on every quote read.
 */
@Entity
@Table(
        name = "market_observations",
        indexes = @Index(
                name = "idx_market_observations_instrument_observed",
                columnList = "instrument_id, observed_at"))
public class MarketObservation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instrument_id", nullable = false)
    private Instrument instrument;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 64)
    private MarketDataSource source;

    /**
     * When this backend stored the quote. Not the provider's market time.
     */
    @Column(name = "observed_at", nullable = false, updatable = false)
    private Instant observedAt;

    /**
     * When the provider says the quote was updated.
     */
    @Column(name = "market_timestamp", nullable = false, updatable = false)
    private Instant marketTimestamp;

    @Enumerated(EnumType.STRING)
    @Column(name = "quality", nullable = false, length = 32)
    private MarketDataQuality quality;

    @Column(name = "price", nullable = false, precision = 19, scale = 4)
    private BigDecimal price;

    @Column(name = "previous_close", precision = 19, scale = 4)
    private BigDecimal previousClose;

    @Column(name = "open_price", precision = 19, scale = 4)
    private BigDecimal openPrice;

    @Column(name = "day_high", precision = 19, scale = 4)
    private BigDecimal dayHigh;

    @Column(name = "day_low", precision = 19, scale = 4)
    private BigDecimal dayLow;

    @Column(name = "volume", nullable = false)
    private long volume;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "session_date")
    private LocalDate sessionDate;

    protected MarketObservation() {
    }

    public MarketObservation(
            Instrument instrument,
            MarketDataSource source,
            Instant observedAt,
            Instant marketTimestamp,
            MarketDataQuality quality,
            BigDecimal price,
            BigDecimal previousClose,
            BigDecimal openPrice,
            BigDecimal dayHigh,
            BigDecimal dayLow,
            long volume,
            String currency,
            LocalDate sessionDate) {
        this.instrument = instrument;
        this.source = source;
        this.observedAt = observedAt;
        this.marketTimestamp = marketTimestamp;
        this.quality = quality;
        this.price = price;
        this.previousClose = previousClose;
        this.openPrice = openPrice;
        this.dayHigh = dayHigh;
        this.dayLow = dayLow;
        this.volume = volume;
        this.currency = currency;
        this.sessionDate = sessionDate;
    }

    public UUID getId() {
        return id;
    }

    public Instrument getInstrument() {
        return instrument;
    }

    public MarketDataSource getSource() {
        return source;
    }

    public Instant getObservedAt() {
        return observedAt;
    }

    public Instant getMarketTimestamp() {
        return marketTimestamp;
    }

    public MarketDataQuality getQuality() {
        return quality;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public BigDecimal getPreviousClose() {
        return previousClose;
    }

    public BigDecimal getOpenPrice() {
        return openPrice;
    }

    public BigDecimal getDayHigh() {
        return dayHigh;
    }

    public BigDecimal getDayLow() {
        return dayLow;
    }

    public long getVolume() {
        return volume;
    }

    public String getCurrency() {
        return currency;
    }

    public LocalDate getSessionDate() {
        return sessionDate;
    }

    @Override
    public final boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) {
            return false;
        }
        MarketObservation that = (MarketObservation) other;
        return id != null && id.equals(that.id);
    }

    @Override
    public final int hashCode() {
        return Hibernate.getClass(this).hashCode();
    }

    @Override
    public String toString() {
        return "MarketObservation{id=" + id + "}";
    }
}
