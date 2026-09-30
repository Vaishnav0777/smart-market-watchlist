-- Core domain schema.
-- Flyway owns this schema. Hibernate validates it and does not generate it.
-- created_at and updated_at are UTC instants (timestamptz).
-- quantity and average_buy_price are exact decimals, not floating-point prices.
-- Instrument rows are identities only. This migration stores no market prices.

CREATE TABLE users (
    id           UUID         PRIMARY KEY,
    email        VARCHAR(254) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_users_email UNIQUE (email)
);

CREATE TABLE instruments (
    id              UUID         PRIMARY KEY,
    symbol          VARCHAR(32)  NOT NULL,
    company_name    VARCHAR(200) NOT NULL,
    exchange        VARCHAR(32)  NOT NULL,
    sector          VARCHAR(100) NOT NULL,
    instrument_type VARCHAR(32)  NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_instruments_exchange_symbol UNIQUE (exchange, symbol)
);

CREATE TABLE watchlists (
    id         UUID         PRIMARY KEY,
    user_id    UUID         NOT NULL,
    name       VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_watchlists_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT uk_watchlists_user_name UNIQUE (user_id, name)
);

CREATE TABLE watchlist_items (
    id            UUID        PRIMARY KEY,
    watchlist_id  UUID        NOT NULL,
    instrument_id UUID        NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL,
    updated_at    TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_watchlist_items_watchlist FOREIGN KEY (watchlist_id) REFERENCES watchlists (id) ON DELETE RESTRICT,
    CONSTRAINT fk_watchlist_items_instrument FOREIGN KEY (instrument_id) REFERENCES instruments (id) ON DELETE RESTRICT,
    CONSTRAINT uk_watchlist_items_watchlist_instrument UNIQUE (watchlist_id, instrument_id)
);

CREATE INDEX idx_watchlist_items_instrument_id ON watchlist_items (instrument_id);

CREATE TABLE portfolios (
    id         UUID         PRIMARY KEY,
    user_id    UUID         NOT NULL,
    name       VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_portfolios_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT uk_portfolios_user_name UNIQUE (user_id, name)
);

CREATE TABLE positions (
    id                UUID          PRIMARY KEY,
    portfolio_id      UUID          NOT NULL,
    instrument_id     UUID          NOT NULL,
    quantity          NUMERIC(19,4) NOT NULL,
    average_buy_price NUMERIC(19,4) NOT NULL,
    created_at        TIMESTAMPTZ   NOT NULL,
    updated_at        TIMESTAMPTZ   NOT NULL,
    CONSTRAINT fk_positions_portfolio FOREIGN KEY (portfolio_id) REFERENCES portfolios (id) ON DELETE RESTRICT,
    CONSTRAINT fk_positions_instrument FOREIGN KEY (instrument_id) REFERENCES instruments (id) ON DELETE RESTRICT,
    CONSTRAINT uk_positions_portfolio_instrument UNIQUE (portfolio_id, instrument_id)
);

CREATE INDEX idx_positions_instrument_id ON positions (instrument_id);
