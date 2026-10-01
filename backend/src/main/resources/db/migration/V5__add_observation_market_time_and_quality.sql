-- Separate the provider's quote time from the server observation time,
-- and record the quality of that quote. Earlier migrations are left untouched.
-- Existing rows stored the provider time in observed_at, so that value is
-- copied into market_timestamp. New writes set the two columns independently.

ALTER TABLE market_observations
    ADD COLUMN market_timestamp TIMESTAMPTZ,
    ADD COLUMN quality VARCHAR(32);

UPDATE market_observations
SET market_timestamp = observed_at
WHERE market_timestamp IS NULL;

UPDATE market_observations
SET quality = 'END_OF_DAY'
WHERE quality IS NULL;

UPDATE market_observations
SET source = 'MOCK'
WHERE lower(source) = 'mock';

ALTER TABLE market_observations
    ALTER COLUMN market_timestamp SET NOT NULL;

ALTER TABLE market_observations
    ALTER COLUMN quality SET NOT NULL;
