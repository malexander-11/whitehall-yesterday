# PRD  
# ONS Daily Statistical Release Integration (MVP+)

---

## 1. Overview

**Feature Name:** ONS Daily Statistical Layer  
**Type:** MVP+ Enhancement (Data Expansion & Insight Layer)

### Summary

Extend “Yesterday on GOV.UK” to ingest and surface **ONS statistical releases published yesterday**, integrating them into the canonical daily index alongside GOV.UK publications.

This feature:

- Expands the service beyond departmental publishing
- Introduces structured statistical releases
- Strengthens the product’s positioning as a daily government intelligence layer

This remains a **date-bound daily index**, not a data exploration tool.

The feature builds on the core product vision described in the Product Overview :contentReference[oaicite:0]{index=0} and supports the broader goal of deepening technical understanding and AI-enabled development :contentReference[oaicite:1]{index=1}.

---

## 2. Problem Statement

ONS statistical releases:

- Are published separately from GOV.UK Search ingestion
- Often contain high-impact national data
- Are not integrated into a unified cross-government daily index
- Require manual monitoring via multiple feeds

There is no single canonical view of:

> “What official UK government data was released yesterday?”

---

## 3. Goals & Non-Goals

### Goals

1. Ingest ONS releases published yesterday
2. Store them in the canonical data model
3. Integrate into the existing daily index
4. Enable filtering by source (GOV.UK vs ONS)
5. Preserve backend-first architecture principles
6. Maintain ingestion reliability and idempotency

---

### Non-Goals (MVP+)

- Full ONS dataset browsing
- Time-series visualisations
- Charting
- Historical bulk ingestion
- Advanced analytics
- BI tooling

This remains a **daily release index**, not an analytics platform.

---

## 4. Target Users

- Policy professionals
- Analysts and researchers
- Journalists
- Public affairs teams
- Consultants and advisory firms

---

## 5. User Stories

### Policy Analyst
As a policy analyst,  
I want to see statistical releases published yesterday  
So that I can identify new data affecting my work.

### Journalist
As a journalist,  
I want ONS releases integrated with policy publishing  
So I can detect alignment between narrative and data.

### Consultant
As a consultant,  
I want to filter statistical releases by theme  
So I can focus on relevant domains (e.g., labour market, inflation).

---

## 6. Data Source

### ONS API Source

Use the **ONS Release Calendar API** as the primary source.

Expected fields:

- Title
- Summary
- Publication date
- Release type
- URI
- Theme
- Dataset links

Rationale:

- Aligns with date-based concept (“Yesterday”)
- Matches release-level granularity
- Avoids dataset-level complexity

---

## 7. Functional Requirements

### 7.1 Ingestion

The daily scheduled job must:

1. Fetch ONS releases for target date (Europe/London timezone)
2. Transform into canonical internal model
3. Store in `items`
4. Insert into `daily_index`
5. Log ingestion in `ingestion_runs`
6. Be idempotent per date

ONS ingestion failure must **not block GOV.UK ingestion**.

---

### 7.2 Data Model Changes

Extend `items` table with:

- `source` (ENUM: GOVUK | ONS)
- `statistical_release_type` (nullable)
- `theme` (nullable)
- `dataset_links` (JSONB, nullable)

No separate ONS table.

Maintain single source of truth.

---

### 7.3 Deduplication

Deduplication rules:

1. Prefer GOV.UK version if identical URL
2. Secondary match on:
   - Normalised title
   - Publication date

---

### 7.4 API Changes

Extend existing endpoint:
GET /v1/days/{date}


Additional response fields:

- `source`
- `statisticalReleaseType`
- `theme`

Optional filter:


GET /v1/days/{date}?source=ONS


No breaking API changes.

---

### 7.5 UI Changes (Minimal)

Add:

- “ONS” badge
- Source filter toggle
- Optional visual differentiation

No redesign required.

Frontend remains presentation-only.

---

## 8. Non-Functional Requirements

| Area | Requirement |
|------|-------------|
| Reliability | Idempotent per date |
| Isolation | ONS failure does not block GOV.UK ingestion |
| Performance | Ingestion < 5s typical |
| Observability | Separate source-level counts |
| Cost | No additional infrastructure |
| Timezone | Europe/London canonical definition |

---

## 9. Architecture Impact

### Updated Ingestion Flow


Scheduled Job
→ GOV.UK Ingestion
→ ONS Ingestion
→ Merge & Index


Each ingestion:

- Independently logged
- Independently idempotent
- Aggregated into daily run summary

Preserves backend-first architecture.

---

## 10. Risks & Mitigations

| Risk | Mitigation |
|------|------------|
| ONS API instability | Store raw payload JSON |
| Duplicate content | Strong canonical URL rule |
| Scope creep | Strict date-bound ingestion |
| Increased complexity | No new service layer |

---

## 11. Success Metrics

### Operational

- % successful ONS ingestion runs
- ONS items per day
- Ingestion duration

### Product

- % of users filtering by source
- Increase in daily item count
- AI summary usage on ONS items

### Strategic

Shift positioning from:

> “GOV.UK publishing tracker”

To:

> “UK Government Daily Intelligence Layer”

---

## 12. Rollout Plan

1. Schema migration
2. ONS API client implementation
3. Manual ingestion testing
4. API contract extension
5. UI badge & filter
6. Production deploy
7. Monitor for 7 days

No feature flag required.

---

## 13. Future Extensions Enabled

This feature unlocks:

- Cross-linking policy + statistical releases
- Economic signal detection
- Impact classification
- Thematic clustering across data + policy
- Sector-based alerting
- Time-series integration (future phase)

---

## 14. Strategic Alignment

This feature:

- Expands core concept without architectural distortion
- Reinforces backend ownership of logic
- Strengthens extensibility for AI enrichment
- Deepens understanding of canonical modelling
- Supports deliberate AI-assisted development

It aligns with:

- The product’s long-term vision :contentReference[oaicite:2]{index=2}
- The motivation to build something technically meaningful and AI-enabled :contentReference[oaicite:3]{index=3}

---

## Final Assessment

This is a strong MVP+ feature because it:

- Expands signal quality
- Preserves architectural integrity
- Avoids premature analytics complexity
- Moves the product toward becoming a structured public data utility

It adds signal, not surface area.