Yesterday on GOV.UK
Phase 0 – v1 Specification (Locked)
1. Product Definition

v1 is a backend-first daily index service with a simple HTML frontend that displays a collection of UK government items that were newly published or publicly updated yesterday (00:00–23:59 Europe/London).

Sources included in v1:

GOV.UK

ONS

UK Parliament

Items are:

Clearly tagged as NEW or UPDATED

Persisted in a canonical database

Available via a read-only API

Displayed in a simple integrated list UI

This is a daily index — not a news site, archive, or alert system.

2. Definition of “Yesterday”

Timezone: Europe/London
Window: 00:00:00 – 23:59:59 (calendar day)

An item is included in {date} if:

Condition	Bucket
published_at within date	NEW
updated_at within date AND published_at < date	UPDATED

Rules:

Time comparisons are done in backend only.

Frontend does not compute date logic.

DST transitions must be handled using timezone-aware libraries.

If both published and updated occur within the same day, bucket = NEW.

3. Scope (v1)
In Scope

Daily ingestion from:

GOV.UK Search API

ONS API / feeds

Parliament API / feeds

Canonical storage in Postgres

Idempotent ingestion per date

Single integrated list view

Filtering by:

Source

Document type (where available)

Clear NEW vs UPDATED tagging

Operational run tracking

Health endpoints

Simple HTML frontend

Items persisted going forward (no backfill)

Out of Scope

Historical backfill

Real-time ingestion

Notifications

User authentication

AI summaries

Impact classification

Advanced analytics

Multi-country ingestion

4. Expected Volume & Performance Assumptions

Expected daily volume: 10–1000 items

System must:

Handle up to 1000 items/day

Complete ingestion in <10 minutes

Be ready by 03:00 Europe/London daily

Downtime tolerance: non-critical
If ingestion fails, site must remain functional (see failure handling below).

5. SLA Definition

Daily ingestion scheduled for 02:00 Europe/London

Yesterday’s index available by 03:00

If ingestion fails:

System continues serving last successful date

Banner displayed:

“Yesterday data not available yet. Showing last successful date: YYYY-MM-DD”

Run marked FAILED in ledger

6. Architecture Principles

Kotlin + Spring Boot API is system of record

Only backend:

Talks to external APIs

Computes yesterday logic

Writes to database

Frontend:

Reads from versioned API only

Contains no business logic

Postgres is canonical store

Docker Compose deployment (single host)

7. Canonical Data Model
items
Field	Type
id	text (PK; canonical_id)
source	enum (govuk, ons, parliament)
title	text
url	text
published_at	timestamp with tz
updated_at	timestamp with tz nullable
tags	jsonb
raw	jsonb
created_at	timestamp
updated_at	timestamp
Canonical ID Strategy

canonical_id = sha256(source + ":" + url)

Where stable external IDs exist (e.g., GOV.UK content_id), they are stored in tags but canonical_id remains stable.

daily_index
Field	Type
date	date
canonical_id	FK → items.id
bucket	enum (NEW, UPDATED)

Unique constraint:
(date, canonical_id)

On rerun:

Delete all rows for date

Recompute deterministically

ingestion_runs
Field	Type
id	uuid
date	date
started_at	timestamp
finished_at	timestamp
status	enum (RUNNING, SUCCESS, FAILED)
total_count	int
source_counts	jsonb
error_summary	text
8. Idempotency Strategy

For a given {date}:

Create ingestion_runs row → status RUNNING

Fetch all sources

Upsert into items by canonical_id

Delete from daily_index where date = {date}

Insert recalculated rows

Mark ingestion_runs SUCCESS

If rerun:

Same deterministic result

No duplicates

9. Error Strategy
Retry Policy

3 retries

Exponential backoff

Timeout on HTTP requests

Failure Rules

If any source fails entirely:

Entire run marked FAILED

daily_index not updated

Site continues serving last successful date

Partial ingestion is not considered successful.

Error Summary Stored

Source name

Error message

Pagination state if relevant

10. Observability (Minimal but Real)
Health Endpoints

GET /health → service up

GET /ready → DB connected + migrations applied

Operational Visibility

GET /ops/runs (last 7 runs)

Run duration logged

Source counts logged

Status clearly visible

11. API Contract (v1)
GET /v1/days/{date}

Returns:

{
  "date": "2026-02-27",
  "counts": {
    "total": 312,
    "new": 210,
    "updated": 102
  },
  "items": [
    {
      "id": "abc123",
      "source": "govuk",
      "title": "Title",
      "url": "https://...",
      "bucket": "NEW",
      "publishedAt": "...",
      "updatedAt": "...",
      "tags": {}
    }
  ]
}
GET /v1/items/{id}

Returns canonical item.

GET /health
GET /ready
12. Success Criteria (v1 Complete When)

Yesterday defined unambiguously

Multi-source ingestion works

Idempotent per date

Rerun safe

Run ledger visible

Ready by 03:00 daily

No duplicate records

Frontend contains no business logic

13. Explicit Non-Goals

This product is not:

A news summariser

A political analysis engine

A real-time monitor

An alert system

An AI insight platform (yet)

It is a daily structured index.

Phase 0 Status

All major product, technical, reliability, and operational decisions are now locked.

Phase 1 may begin.