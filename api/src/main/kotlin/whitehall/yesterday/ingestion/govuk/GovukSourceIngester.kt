package whitehall.yesterday.ingestion.govuk

import org.springframework.stereotype.Component
import whitehall.yesterday.ingestion.DateWindow
import whitehall.yesterday.ingestion.ItemRow
import whitehall.yesterday.ingestion.SourceIngester
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * Fetches and normalises GOV.UK search results into [ItemRow]s.
 *
 * The actual HTTP pagination is delegated to [GovukClient]; this class handles
 * mapping raw [GovukSearchResult]s to the canonical item model.
 */
@Component
class GovukSourceIngester(private val govukClient: GovukClient) : SourceIngester {

    override val sourceName: String = "govuk"

    override fun fetchItems(window: DateWindow): List<ItemRow> =
        govukClient.fetchAll(window).mapNotNull { mapToItem(it, window) }

    /**
     * Maps a raw GOV.UK result to an [ItemRow], returning null if the item's
     * public_timestamp falls outside the window (boundary items on the last page).
     */
    fun mapToItem(result: GovukSearchResult, window: DateWindow): ItemRow? {
        val link = result.link ?: return null
        val url  = "https://www.gov.uk$link"

        val id = result.contentId?.takeIf { it.isNotBlank() } ?: sha256("govuk:$url")

        val publicTimestamp = result.publicTimestamp?.let { parseTimestamp(it) } ?: return null

        if (publicTimestamp < window.start || publicTimestamp >= window.end) return null

        val firstPublishedAt = result.firstPublishedAt?.let { parseTimestamp(it) }

        val publishedAt = firstPublishedAt ?: publicTimestamp
        val updatedAt   = if (firstPublishedAt != null && publicTimestamp != firstPublishedAt) publicTimestamp else null

        return ItemRow(
            id          = id,
            source      = "govuk",
            title       = result.title?.ifBlank { null } ?: "Untitled",
            url         = url,
            publishedAt = publishedAt,
            updatedAt   = updatedAt,
            tags        = buildTags(result),
            raw         = result
        )
    }

    private fun buildTags(result: GovukSearchResult): Map<String, Any?> =
        mapOf(
            "format"        to result.format,
            "organisations" to result.organisations?.mapNotNull { it["slug"] as? String },
            "native_ids"    to mapOf("content_id" to result.contentId)
        ).filterValues { it != null }

    // GOV.UK returns ISO datetimes for public_timestamp but plain dates (e.g. "2025-03-31")
    // for first_published_at on older content. Handle both formats.
    private fun parseTimestamp(s: String): Instant =
        try { OffsetDateTime.parse(s).toInstant() }
        catch (_: Exception) { LocalDate.parse(s).atStartOfDay(ZoneOffset.UTC).toInstant() }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
