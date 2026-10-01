package com.smartwatch.portfolio.entity;

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

import java.math.BigDecimal;

/**
 * A user's holding of one instrument inside a portfolio.
 *
 * <p>The current market price is not stored here. Price observations come
 * from {@code MarketDataProvider}. {@code quantity} and {@code averageBuyPrice}
 * are {@link BigDecimal} values mapped to {@code NUMERIC(19,4)}.
 * One portfolio has at most one position per instrument.
 */
@Entity
@Table(
        name = "positions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_positions_portfolio_instrument",
                columnNames = {"portfolio_id", "instrument_id"}),
        indexes = @Index(name = "idx_positions_instrument_id", columnList = "instrument_id"))
public class Position extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private Portfolio portfolio;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instrument_id", nullable = false)
    private Instrument instrument;

    @Column(name = "quantity", nullable = false, precision = 19, scale = 4)
    private BigDecimal quantity;

    @Column(name = "average_buy_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal averageBuyPrice;

    protected Position() {
    }

    public Position(Portfolio portfolio, Instrument instrument, BigDecimal quantity, BigDecimal averageBuyPrice) {
        this.portfolio = portfolio;
        this.instrument = instrument;
        this.quantity = quantity;
        this.averageBuyPrice = averageBuyPrice;
    }

    public Portfolio getPortfolio() {
        return portfolio;
    }

    public Instrument getInstrument() {
        return instrument;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getAverageBuyPrice() {
        return averageBuyPrice;
    }

    public void changeHolding(BigDecimal quantity, BigDecimal averageBuyPrice) {
        this.quantity = quantity;
        this.averageBuyPrice = averageBuyPrice;
    }

    @Override
    public String toString() {
        return "Position{id=" + getId() + ", quantity=" + quantity + ", averageBuyPrice=" + averageBuyPrice + "}";
    }
}
