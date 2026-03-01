package whitehall.yesterday.ingestion

/**
 * Common interface for all source-specific ingestion logic.
 *
 * Each implementation fetches items from a single upstream source and returns
 * them normalised as [ItemRow]s.  Bucket classification (NEW vs UPDATED) is
 * handled by the orchestrating [IngestionService].
 */
interface SourceIngester {
    /** Short identifier used in source_counts (e.g. "govuk", "parliament_bill"). */
    val sourceName: String

    /**
     * Fetch all items from this source that fall within the given [window].
     * Must be idempotent — repeated calls for the same window return the same items.
     * Throws on unrecoverable errors so the caller can mark the run as FAILED.
     */
    fun fetchItems(window: DateWindow): List<ItemRow>
}
