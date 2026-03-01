-- Ledger of every ingest attempt.
-- One row is created (status = RUNNING) when a run starts.
-- On completion it is updated to SUCCESS or FAILED.
-- source_counts is a JSON object: { "govuk": 312 }
-- error_summary is populated only on failure.

CREATE TABLE ingestion_runs (
    id            uuid            PRIMARY KEY DEFAULT gen_random_uuid(),
    date          date            NOT NULL,
    started_at    timestamptz     NOT NULL,
    finished_at   timestamptz,
    status        run_status_enum NOT NULL,
    total_count   int             NOT NULL DEFAULT 0,
    source_counts jsonb           NOT NULL DEFAULT '{}',
    error_summary text
);

CREATE INDEX idx_ingestion_runs_date ON ingestion_runs (date);
