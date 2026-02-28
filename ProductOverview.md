# Product Overview  
## Whitehall Yesterday

---

## 1. Purpose

Whitehall Yesterday is a daily index service that provides a structured, reliable view of UK government information published or updated the previous calendar day.

It transforms fragmented publishing across departments and institutions into a single, time-bound, filterable collection.

The core value is clarity:
- A definitive daily collection
- Clear distinction between new and updated items
- A simple, consistent way to monitor government output


---

## 2. The Problem

UK government information is published across multiple platforms:

- GOV.UK
- Office for National Statistics (ONS)
- UK Parliament

Each platform has:
- Different structures
- Different taxonomies
- Different update patterns

There is no single, authoritative, daily “what was published yesterday?” view.

Professionals must:
- Monitor multiple feeds
- Manually track updates
- Infer what materially changed

---

## 3. The Solution

The product provides:

### 1️⃣ A Canonical Daily Index

A structured, date-based index of:

- All newly published content
- All publicly updated content
- Clearly tagged as NEW or UPDATED
- Persisted going forward

All items link directly to their official source.

---

### 2️⃣ A Single Integrated View

Content from:
- GOV.UK
- ONS
- Parliament

Presented in one unified list with source tagging.

Users can filter by:
- Source
- Document type (where available)

---

### 3️⃣ Operational Reliability

The system is:
- Timezone-aware (Europe/London)
- Idempotent per date
- Tracked via ingestion ledger
- Designed to be deterministic and auditable

If ingestion fails, the site continues serving the last successful date.

---

## 4. Target Users

Primary:
- Policy professionals
- Analysts and researchers
- Journalists
- Public affairs teams
- Consultants and advisory firms

Secondary:
- Interested citizens
- Academics
- Advocacy groups

---

## 5. What This Product Is Not

This product is not:

- A real-time monitoring tool
- A notification system
- A political analysis engine
- A summarisation platform
- A historical archive (initially)

It is a daily structured index.

---

## 6. Long-Term Direction

The architecture supports future evolution into:

- AI-assisted summarisation
- Thematic clustering
- Subscription alerts
- Semantic search
- Historical analytics

These are intentionally out of scope for v1.

---

## 7. Guiding Principle

Prioritise:

- Correctness
- Determinism
- Operational clarity
- Backend ownership
- Minimal UI complexity

The integrity of the daily index is the product.