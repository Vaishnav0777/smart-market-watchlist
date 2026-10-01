-- Provider-specific instrument keys. The internal symbol stays the watchlist identity.
-- An Upstox key looks like NSE_EQ|INE002A01018. This migration stores no keys and no prices.

CREATE TABLE instrument_provider_keys (
    id                       UUID         PRIMARY KEY,
    instrument_id            UUID         NOT NULL,
    provider                 VARCHAR(32)  NOT NULL,
    external_instrument_key  VARCHAR(128) NOT NULL,
    created_at               TIMESTAMPTZ  NOT NULL,
    updated_at               TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_instrument_provider_keys_instrument
        FOREIGN KEY (instrument_id) REFERENCES instruments (id) ON DELETE RESTRICT,
    CONSTRAINT uk_instrument_provider_keys_provider_instrument
        UNIQUE (provider, instrument_id),
    CONSTRAINT uk_instrument_provider_keys_provider_key
        UNIQUE (provider, external_instrument_key)
);

CREATE INDEX idx_instrument_provider_keys_provider
    ON instrument_provider_keys (provider);
