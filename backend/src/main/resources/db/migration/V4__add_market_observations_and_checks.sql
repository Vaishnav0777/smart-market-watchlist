-- Market observations and explicit watchlist check cursors.
-- Flyway owns this change. Earlier migrations are left untouched.
-- Observations are reference points recorded when a user checks a watchlist.
-- This migration inserts no users and no market prices.

CREATE TABLE market_observations (
    id              UUID          PRIMARY KEY,
    instrument_id   UUID          NOT NULL,
    source          VARCHAR(64)   NOT NULL,
    observed_at     TIMESTAMPTZ   NOT NULL,
    price           NUMERIC(19,4) NOT NULL,
    previous_close  NUMERIC(19,4),
    open_price      NUMERIC(19,4),
    day_high        NUMERIC(19,4),
    day_low         NUMERIC(19,4),
    volume          BIGINT        NOT NULL,
    currency        VARCHAR(3)    NOT NULL,
    session_date    DATE,
    CONSTRAINT fk_market_observations_instrument FOREIGN KEY (instrument_id) REFERENCES instruments (id) ON DELETE RESTRICT
);

CREATE INDEX idx_market_observations_instrument_observed
    ON market_observations (instrument_id, observed_at);

CREATE TABLE watchlist_checks (
    id           UUID        PRIMARY KEY,
    watchlist_id UUID        NOT NULL,
    user_id      UUID        NOT NULL,
    checked_at   TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_watchlist_checks_watchlist FOREIGN KEY (watchlist_id) REFERENCES watchlists (id) ON DELETE CASCADE,
    CONSTRAINT fk_watchlist_checks_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT
);

CREATE INDEX idx_watchlist_checks_watchlist_checked
    ON watchlist_checks (watchlist_id, checked_at);

CREATE TABLE watchlist_check_items (
    id              UUID PRIMARY KEY,
    check_id        UUID NOT NULL,
    instrument_id   UUID NOT NULL,
    observation_id  UUID,
    CONSTRAINT fk_watchlist_check_items_check FOREIGN KEY (check_id) REFERENCES watchlist_checks (id) ON DELETE CASCADE,
    CONSTRAINT fk_watchlist_check_items_instrument FOREIGN KEY (instrument_id) REFERENCES instruments (id) ON DELETE RESTRICT,
    CONSTRAINT fk_watchlist_check_items_observation FOREIGN KEY (observation_id) REFERENCES market_observations (id) ON DELETE RESTRICT,
    CONSTRAINT uk_watchlist_check_items_check_instrument UNIQUE (check_id, instrument_id)
);
