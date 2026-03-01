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
    val raw: Any      // verbatim upstream response — serialised to JSON by the repository
)
