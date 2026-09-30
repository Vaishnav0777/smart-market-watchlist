package com.smartwatch.marketdata.entity;

import com.smartwatch.common.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * An identifiable financial instrument.
 *
 * <p>This is not a quote and it does not store a market price. The same
 * symbol may exist on more than one exchange, so identity is
 * {@code exchange + symbol}.
 */
@Entity
@Table(
        name = "instruments",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_instruments_exchange_symbol",
                columnNames = {"exchange", "symbol"}))
public class Instrument extends AuditableEntity {

    @Column(name = "symbol", nullable = false, length = 32)
    private String symbol;

    @Column(name = "company_name", nullable = false, length = 200)
    private String companyName;

    @Column(name = "exchange", nullable = false, length = 32)
    private String exchange;

    @Column(name = "sector", nullable = false, length = 100)
    private String sector;

    @Enumerated(EnumType.STRING)
    @Column(name = "instrument_type", nullable = false, length = 32)
    private InstrumentType instrumentType;

    protected Instrument() {
    }

    public Instrument(
            String symbol,
            String companyName,
            String exchange,
            String sector,
            InstrumentType instrumentType) {
        this.symbol = symbol;
        this.companyName = companyName;
        this.exchange = exchange;
        this.sector = sector;
        this.instrumentType = instrumentType;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getCompanyName() {
        return companyName;
    }

    public String getExchange() {
        return exchange;
    }

    public String getSector() {
        return sector;
    }

    public InstrumentType getInstrumentType() {
        return instrumentType;
    }

    @Override
    public String toString() {
        return "Instrument{id=" + getId() + ", exchange='" + exchange + "', symbol='" + symbol + "'}";
    }
}
