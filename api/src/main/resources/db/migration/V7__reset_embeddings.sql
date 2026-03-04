-- V7: Reset all embeddings so they are regenerated with the richer corpus
-- (title + organisation names instead of title + description, which was always empty).
-- After deploying, run: POST /ops/embeddings/backfill?limit=1000 (repeat until processed=0)
UPDATE items SET embedding = NULL;
