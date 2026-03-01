-- Rename the LATEST bucket value to UPDATED.
-- LATEST was the original name during schema design; UPDATED is more self-explanatory
-- and consistent with the product spec's "NEW vs UPDATED" language.
-- Safe to run: no rows exist in daily_index yet.
ALTER TYPE bucket_enum RENAME VALUE 'LATEST' TO 'UPDATED';
