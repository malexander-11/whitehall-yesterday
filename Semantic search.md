# Hybrid Search (Per-Day Semantic Retrieval)

**Product:** Whitehall Yesterday  
**Author:** Matt  
**Status:** Ready for Engineering  
**Version:** v1  

---

## 1. Overview

Hybrid Search enables users to retrieve the most relevant publications from a specific day using natural language queries.

Search operates **strictly within a given day's indexed results**. It does not search across historical data.

The goal is to allow users to quickly identify what was published by Whitehall on that day that matters to them.

---

## 2. Problem Statement

The daily index can contain hundreds of items.

Even with structured categorisation, users must manually scan content to find relevant publications.

This creates:

- Cognitive overload  
- Scanning fatigue  
- Risk of missing relevant material  

Users need a way to express intent and retrieve relevant results without scanning the full list.

---

## 3. Feature Scope

### In Scope (v1)

- Hybrid search within a single day's results
- Text input field
- Search button (explicit click required — no live search)
- Results ranked by hybrid scoring
- Replace day list view with ranked search results when search is active
- Return to full list when search is cleared

### Out of Scope (v1)

- Cross-day search
- Filters
- Autocomplete
- Query suggestions
- Highlighting
- Saved searches
- Ranking transparency
- Search analytics
- Relevance tuning UI

---

## 4. UX Requirements

### Placement

Search bar appears:

Banner  
→ Day header (e.g. "2 March 2026")  
→ **Search input + button**  
→ Results list  

Search sits **above results but below date context**.

---

### Interaction Model

- User types query
- User clicks "Search"
- API request is triggered
- Results list is replaced with ranked search results

Search is NOT triggered:
- On typing
- On debounce

Explicit button click only.

---

### Reset Behaviour

When:
- Query is cleared and search button clicked  
OR  
- A "Clear search" button is clicked  

The original daily list is restored.
---

## 5. Search Definition

Search operates only within:


daily_index WHERE date = {selectedDate}


No cross-day expansion.

---

## 6. Technical Design

### 6.1 Hybrid Retrieval Strategy

Hybrid search combines:

1. Lexical full-text search (Postgres `tsvector`)
2. Semantic similarity search (pgvector embeddings)

#### Retrieval Steps

For given `{date}` and `{query}`:

1. Retrieve all content_ids for that date
2. Generate query embedding
3. Run:
   - Lexical search over that day's items  
   - Vector similarity search over that day's items  
4. Union candidates
5. Score and rank
6. Return top N (default 50)

---

### 6.2 Data Model Changes

#### items table additions

```sql
ALTER TABLE items
ADD COLUMN search_tsv tsvector;

CREATE INDEX idx_items_search_tsv
ON items
USING GIN (search_tsv);

search_tsv generated from:

title

description

Embeddings

Option A (simplest for v1):

ALTER TABLE items
ADD COLUMN embedding vector(1536);

Index:

CREATE INDEX idx_items_embedding
ON items
USING ivfflat (embedding vector_cosine_ops);

(Exact index type depends on pgvector version.)

6.3 Embedding Generation

Embeddings generated:

After ingestion

Via async worker

One embedding per item

Text used: title + description

Embeddings regenerated only if:

Title or description changes

7. Ranking Logic
Candidate Pool

For selected day:

Top 200 lexical matches

Top 200 semantic matches

Union + dedupe.

Scoring Formula (Initial)

Normalised scores:

final_score =
  0.6 * semantic_score
+ 0.4 * lexical_score

No:

Freshness boost (already same day)

Bucket boost

Department weighting

Keep it simple for v1.

8. API Design
Endpoint
POST /v1/days/{date}/search
Request
{
  "query": "AI regulation fintech",
  "limit": 50,
  "offset": 0
}
Response

Same DTO as normal day list:

{
  "date": "2026-03-02",
  "query": "AI regulation fintech",
  "totalResults": 37,
  "items": [
    {
      "id": "...",
      "title": "...",
      "documentType": "...",
      "organisations": [...],
      "publishedAt": "...",
      "bucket": "NEW"
    }
  ]
}

No ranking explanation.
No score exposure.

9. Performance Requirements

Target latency:

< 1 second end-to-end

Includes:

Query embedding generation

DB lexical search

DB vector search

Ranking

Response serialization

10. Non-Functional Requirements

Search must not block ingestion pipeline

Embedding generation must be async

Failure to generate embedding should not block item visibility

Search endpoint must degrade gracefully if embedding missing

System must operate within Fly.io resource constraints

11. Edge Cases
Scenario	Behaviour
Empty query	Return default list
Query < 3 chars	Return 400
No matches	Return empty list
Embedding service timeout	Fallback to lexical-only search
Day not found	404
12. Risks & Mitigations
Risk: Embedding latency slows search

Mitigation:

Cache query embeddings (future)

Bound candidate pool

Risk: Over-ranking semantically vague items

Mitigation:

Keep lexical weight meaningful (0.4)

Risk: Postgres memory pressure

Mitigation:

Restrict search to day subset before ranking

13. Definition of Done

Embeddings stored for all items

search_tsv populated

Endpoint returns ranked results

UI renders search above results

Search only triggered via button click

Latency under 1 second

Tested with days containing 300+ items

14. Rollout Plan

Deploy embedding migration

Backfill embeddings for existing items

Deploy search endpoint

Release UI search bar

Monitor CPU + memory on Fly

Manually test varied queries

15. Future Evolution (Not in this version)

Cross-day search

Filters

Highlighting

Query analytics

Weight tuning

Storyline clustering

“Ask over yesterday” (RAG)