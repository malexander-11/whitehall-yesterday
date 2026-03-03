CREATE EXTENSION IF NOT EXISTS vector;

ALTER TABLE items
  ADD COLUMN search_tsv tsvector
    GENERATED ALWAYS AS (
      to_tsvector('english',
        coalesce(title, '') || ' ' || coalesce(tags->>'description', '')
      )
    ) STORED,
  ADD COLUMN embedding vector(512);

CREATE INDEX idx_items_search_tsv
  ON items USING GIN (search_tsv);

CREATE INDEX idx_items_embedding
  ON items USING hnsw (embedding vector_cosine_ops);
