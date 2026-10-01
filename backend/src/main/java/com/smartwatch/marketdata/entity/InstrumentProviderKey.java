package com.smartwatch.marketdata.entity;

import com.smartwatch.common.AuditableEntity;
import com.smartwatch.marketdata.model.MarketDataSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * A provider's own identifier for an instrument we already store.
 *
 * <p>The internal identity remains {@code exchange + symbol}. This row holds
 * the external key, for example {@code NSE_EQ|INE002A01018} for Upstox.
 */
@Entity
@Table(
        name = "instrument_provider_keys",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_instrument_provider_keys_provider_instrument",
                        columnNames = {"provider", "instrument_id"}),
                @UniqueConstraint(
                        name = "uk_instrument_provider_keys_provider_key",
                        columnNames = {"provider", "external_instrument_key"})
        })
public class InstrumentProviderKey extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instrument_id", nullable = false)
    private Instrument instrument;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 32)
    private MarketDataSource provider;

    @Column(name = "external_instrument_key", nullable = false, length = 128)
    private String externalInstrumentKey;

    protected InstrumentProviderKey() {
    }

    public InstrumentProviderKey(Instrument instrument, MarketDataSource provider, String externalInstrumentKey) {
        this.instrument = instrument;
        this.provider = provider;
        this.externalInstrumentKey = externalInstrumentKey;
    }

    public Instrument getInstrument() {
        return instrument;
    }

    public MarketDataSource getProvider() {
        return provider;
    }

    public String getExternalInstrumentKey() {
        return externalInstrumentKey;
    }
}
