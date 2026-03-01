package whitehall.yesterday.ingestion

import java.time.Instant

/** Normalised item ready to be written to the database. Bucket is assigned separately via DB lookup. */
data class ItemRow(
    val id: String,
    val source: String,
    val title: String,
    val url: String,
    val publishedAt: Instant,
    val updatedAt: Instant?,
    val tags: Map<String, Any?>,
    val raw: Any,           // verbatim upstream response — serialised to JSON by the repository
    val sourceSubtype: String? = null,    // "bill" | "si" | null (govuk items omit this)
    val sourceReference: String? = null   // stable external ID, e.g. "bill/1234" or "si/abc123"
)
