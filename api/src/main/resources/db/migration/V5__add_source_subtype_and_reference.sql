ALTER TABLE items
    ADD COLUMN source_subtype   text,
    ADD COLUMN source_reference text;

-- Idempotency index for Parliament items: (source, subtype, reference) must be unique.
-- GOV.UK items have source_reference = NULL so this index does not affect them.
CREATE UNIQUE INDEX idx_items_source_ref
    ON items (source, source_subtype, source_reference)
    WHERE source_reference IS NOT NULL;
