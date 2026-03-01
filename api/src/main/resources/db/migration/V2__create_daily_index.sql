-- One row per (date, item) pair.
-- bucket is NEW when the item was first published on that date;
-- LATEST when it was updated on that date but published earlier.
--
-- The entire date is rebuilt atomically on each ingest run:
--   DELETE FROM daily_index WHERE date = :date;
--   INSERT recalculated rows;
-- This makes reruns safe and deterministic.

CREATE TABLE daily_index (
    date         date        NOT NULL,
    canonical_id text        NOT NULL REFERENCES items (id),
    bucket       bucket_enum NOT NULL,
    CONSTRAINT pk_daily_index PRIMARY KEY (date, canonical_id)
);

CREATE INDEX idx_daily_index_date ON daily_index (date);
