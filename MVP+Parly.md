Feature: Add Parliament documents to “Yesterday” index (MVP)

Problem
Users monitoring UK policy/regulation must track both:

executive publishing (GOV.UK)

legislative activity (Bills + SIs)

There’s no single daily view for both, increasing cognitive load and risk of missing legislative signals.

Goal
Extend the daily index to include Parliament Bills introduced and Statutory Instruments laid on the target date (“yesterday”, Europe/London).

Note
Two useful APIs statutoryinstruments-api.parliament.uk/ and bills-api.parliament.uk

In scope

Ingest Bills introduced on date D

Ingest SIs laid on date D

Normalise into existing canonical items

Add to daily_index for date D

Bucket = NEW only

Filters in UI by source + subtype

Link to official Parliament pages

Out of scope

Bill stages, amendments, votes

SI procedure deep analysis

“UPDATED” logic for Parliament

AI summaries for Parliament

Historic backfill beyond a small lookback window

Functional requirements

Ingestion

System shall fetch Parliament Bills and SIs from official APIs.

System shall support pagination and stop conditions.

System shall be idempotent per (source, date).

Normalisation

Each item must have: title, url, public_timestamp (= introduced/laid date), source_type=PARLIAMENT, source_subtype=BILL|SI, source_reference=(official reference/ID).

Indexing

Each ingested item must be indexed into daily_index(date=D, bucket=NEW).

UI

Show badge “Parliament”

Filters: Source + subtype

Non-functional requirements

Ingestion completes < 5 minutes typical day

No duplicates across reruns

Resilient to API timeouts; partial failures recorded

Works reliably on Fly.io deployment model

Success criteria

7 consecutive days of successful Parliament ingestion runs

Re-running ingestion for a date does not increase item counts

UI shows correct counts + filters for GOV.UK vs Parliament

3) Unified data model changes (minimal, extensible)

Add to items:

source_type (GOVUK | PARLIAMENT)

source_subtype (BILL | SI)

source_reference (stable external ID / bill number / SI number)

raw_source_json (store raw payload for safety/debug)

Why raw payload matters: it de-risks schema changes and lets you reprocess without refetching (especially useful given the SI API version transition) .

4) The two “Idk”s you flagged — what to do (without guessing)
A) Idempotency (you’re unsure)

On Fly.io you can end up with two machines running, restarts, deploys, etc. If you rely only on “scheduled job ran once”, you will get duplicates.

Make idempotency data-enforced, not “job-enforced”.

Minimum standard

Create a unique constraint like:

unique(source_type, source_subtype, source_reference)

And a second one for indexing:

unique(date, content_id) in daily_index

This guarantees reruns don’t duplicate rows, even if the job runs twice.

Also: lock ingestion per (source, date) with an ingestion_runs row you “claim” first. If another worker tries, it sees RUNNING/SUCCEEDED and exits.

B) Raw JSON (you’re unsure)

Yes — store it. It’s cheap and saves your life when:

API fields change

you need to debug “why wasn’t this included?”

you later add AI summaries and want provenance

5) “We might need to get all then filter” — correct approach

Design for “no perfect date filter”.

Algorithm (safe + efficient)

Fetch in reverse chronological order (most recent first)

Continue until the items’ laidDate/introducedDate is older than D

Keep only where date == D

Stop early once you’re past D

This avoids pulling “all time” while still not depending on the API supporting date filtering.

6) Fly.io scheduling: don’t rely only on in-app cron

Because you’re deployed on Fly.io: you have two viable patterns.

Preferred (ops-clean): Fly Cron Manager / Machines
Fly has a blueprint for scheduling tasks by spinning up one-off machines.
This is great for “run once daily” jobs, avoids “multiple web instances both think they should schedule”.

Acceptable (if you must): in-app @Scheduled
If you keep Spring @Scheduled, you must add the idempotency + run-claim logic above, because multiple instances can trigger. (Fly community discussions frequently land on “ensure your app is safe under concurrency”. )

Given you’re already deployed: you can keep @Scheduled for now if the database constraints + run-claim exist.

7) Implementation plan (in the right order)

DB migrations

Add source_type, source_subtype, source_reference, raw_source_json

Add unique constraints for idempotency

Refactor ingestion into a unified interface

SourceIngestionService { ingest(date) }

existing GOV.UK becomes one implementation

Parliament becomes another

Implement Parliament ingestion (Bills first, then SIs)

Start with fixed manual date endpoint (POST /v1/ingest/parliament/{date})

Validate stop conditions + filtering

Wire into the scheduler

Run both sources for “yesterday”

Record counts per source in ingestion_runs

Expose in API DTO

Include source badge fields

UI

Add source + subtype filter and badge

Hardening

Retry/backoff

Structured logs of counts + stop condition

Metrics: run duration, fetched pages, stored items
