Ingestion Design (v1 – Locked)
Scope Update

MVP includes:

GOV.UK only

ONS and Parliament are explicitly out of scope for MVP.

3. Sources
3.1 GOV.UK
Endpoint
https://www.gov.uk/api/search.json
Content Scope

All content types returned by search API

No filtering by type in MVP

Required Fields

From search API:

title

link/base_path

public_timestamp

document_type

organisations

topics

Timestamps

Classification logic:

If item first appears with a publish timestamp within the day → tag = NEW

If item has public_timestamp within day but was previously published → tag = UPDATED

However, you’ve chosen to simplify:

If updated timestamp field not clearly distinguishable → use tags NEW and LATEST

So final rule for MVP:

If published within date → NEW

If updated within date but published before → LATEST

4. Canonical Mapping
Canonical Item Shape
id: sha256("govuk:" + full_url)
source: "govuk"
title: string
url: full absolute URL
published_at: timestamptz
updated_at: timestamptz nullable
tags: {
  topic: string[]?,
  organisations: string[]?,
  document_type: string?,
  native_ids: {
    content_id?: string
  }
}
raw: jsonb
5. Date Filtering Logic
Timezone

Europe/London

Day boundary = 00:00–23:59 local time

All comparisons done server-side

Store timestamps as UTC in DB

Inclusion Rules

Pseudocode:

if published_at in day:
    bucket = NEW
else if updated_at in day and published_at < day:
    bucket = LATEST
else:
    exclude

If updated_at missing:

Include as NEW if published_at in day

Otherwise ignore

6. Run Lifecycle
Schedule

Scheduled at 02:00 Europe/London

Must complete by 03:00

Maximum allowed runtime = 1 hour

Manual Ingest

Allowed via POST endpoint

Used for:

Dev testing

Rerun after failure

Manual ingest overwrites items (upsert) and rebuilds index for date.

7. Idempotency Strategy
Items

Upsert by canonical_id

On conflict:

Update title

Update timestamps

Replace raw

Union tag arrays

Daily Index

For date D:

DELETE FROM daily_index WHERE date = D

Insert computed rows for D

Deterministic rebuild every time.

8. Failure Handling
Failure Rule

If GOV.UK ingestion fails entirely:

Mark run FAILED

Do NOT update daily_index

Continue serving last successful date

Partial Success

If pagination partially succeeds:

Items may be stored

daily_index not rebuilt

Run marked FAILED

You’ve chosen Option A:
Keep stored items; don’t rollback.

9. Retry & Backoff

Timeout per request: 30 seconds

Retries: 3

Backoff: exponential

Retry on:

5xx

timeouts

429 (respect Retry-After header)

10. Pagination & Deduplication
Pagination

Loop until no more pages

Track:

page count

total items fetched

Deduplication

If duplicate URL in same run:

Keep record with latest updated_at

Union arrays for tags

Preserve most recent raw payload

11. Observability

No alerting in MVP.

Must include:

/health

/ready

/ops/runs endpoint

Ingestion run must log:

date

total count

new count

latest count

duration

status

12. Testing Strategy

You have committed to:

Golden day JSON fixture

DST boundary unit test

Retry simulation test

Rerun idempotency test