-- Shared enum types used across the schema.
-- source_enum: the publishing platform an item came from.
-- bucket_enum: classification of an item within a daily index.
-- run_status_enum: lifecycle state of an ingestion run.

CREATE TYPE source_enum    AS ENUM ('govuk', 'ons', 'parliament');
CREATE TYPE bucket_enum    AS ENUM ('NEW', 'LATEST');
CREATE TYPE run_status_enum AS ENUM ('RUNNING', 'SUCCESS', 'FAILED');

-- Canonical store of every item ever ingested.
-- id is sha256(source + ":" + url), computed by the backend.
-- published_at / updated_at are sourced from the upstream API (stored as UTC).
-- tags holds structured metadata (organisations, topics, document_type, native_ids).
-- raw holds the verbatim upstream JSON payload.
-- created_at records the first time this item was ingested.
-- modified_at is updated on every upsert so we can see the last ingest time.

CREATE TABLE items (
    id           text            PRIMARY KEY,
    source       source_enum     NOT NULL,
    title        text            NOT NULL,
    url          text            NOT NULL,
    published_at timestamptz     NOT NULL,
    updated_at   timestamptz,
    tags         jsonb           NOT NULL DEFAULT '{}',
    raw          jsonb           NOT NULL DEFAULT '{}',
    created_at   timestamptz     NOT NULL DEFAULT now(),
    modified_at  timestamptz     NOT NULL DEFAULT now()
);

CREATE INDEX idx_items_source       ON items (source);
CREATE INDEX idx_items_published_at ON items (published_at);
