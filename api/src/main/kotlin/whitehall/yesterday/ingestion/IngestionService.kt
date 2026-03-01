package whitehall.yesterday.ingestion

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import whitehall.yesterday.ingestion.govuk.GovukClient
import whitehall.yesterday.ingestion.govuk.GovukSearchResult
import whitehall.yesterday.ingestion.repository.DailyIndexRepository
import whitehall.yesterday.ingestion.repository.IngestionRunRepository
import whitehall.yesterday.ingestion.repository.ItemRepository
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Service
class IngestionService(
    private val govukClient: GovukClient,
    private val itemRepository: ItemRepository,
    private val dailyIndexRepository: DailyIndexRepository,
    private val ingestionRunRepository: IngestionRunRepository,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(IngestionService::class.java)

    /**
     * Runs a full ingest for [date] and returns the final result synchronously.
     *
     * Lifecycle:
     *  1. Open run (status = RUNNING)
     *  2. Fetch all pages from GOV.UK — throws on persistent failure
     *  3. Map raw results to ItemRows, filtering to items active within the window
     *  4. Bulk-query the DB for items already known before the window (→ UPDATED)
     *  5. Upsert each item (auto-committed per row; kept on failure per Option A)
     *  6. Atomically rebuild daily_index for [date]
     *  7. Mark run SUCCESS
     *
     * If step 2 throws, steps 6–7 are skipped: daily_index is untouched and
     * the run is marked FAILED.
     */
    fun ingest(date: LocalDate): IngestionRunResult {
        val window = DateWindow.forDate(date)
        log.info("Ingestion started date={} window=[{}, {})", date, window.start, window.end)

        val runId = ingestionRunRepository.create(date)
        val startedAt = Instant.now()

        return try {
            val rawResults = govukClient.fetchAll(window)
            val items = rawResults.mapNotNull { mapToItem(it, window) }

            // Bucket classification via DB: items seen before this window are UPDATED, new ones are NEW.
            val existingIds = if (items.isEmpty()) emptySet()
                              else itemRepository.existingIds(items.map { it.id }, window.start)

            val itemsWithBuckets: List<Pair<ItemRow, Bucket>> = items.map { item ->
                item to if (item.id in existingIds) Bucket.UPDATED else Bucket.NEW
            }

            log.info(
                "Classified {} items for date={} (fetched={}, new={}, updated={})",
                items.size, date, rawResults.size,
                itemsWithBuckets.count { (_, b) -> b == Bucket.NEW },
                itemsWithBuckets.count { (_, b) -> b == Bucket.UPDATED }
            )

            itemsWithBuckets.forEach { (item, _) -> itemRepository.upsert(item) }

            dailyIndexRepository.rebuild(date, itemsWithBuckets.map { (item, bucket) -> item.id to bucket })

            val totalCount = items.size
            val sourceCounts = mapOf("govuk" to totalCount)
            ingestionRunRepository.markSuccess(runId, totalCount, sourceCounts)

            val durationMs = Duration.between(startedAt, Instant.now()).toMillis()
            log.info("Ingestion complete date={} total={} durationMs={}", date, totalCount, durationMs)

            IngestionRunResult(runId, date, "SUCCESS", totalCount, sourceCounts, durationMs)
        } catch (e: Exception) {
            val durationMs = Duration.between(startedAt, Instant.now()).toMillis()
            log.error("Ingestion failed date={} durationMs={}", date, durationMs, e)
            ingestionRunRepository.markFailed(runId, e.message ?: "Unknown error")
            IngestionRunResult(runId, date, "FAILED", 0, emptyMap(), durationMs, e.message)
        }
    }

    /**
     * Maps a raw GOV.UK result to an [ItemRow], returning null if the item's
     * public_timestamp falls outside the window (boundary items on the last page).
     */
    private fun mapToItem(result: GovukSearchResult, window: DateWindow): ItemRow? {
        val link = result.link ?: return null
        val url  = "https://www.gov.uk$link"

        val id = result.contentId?.takeIf { it.isNotBlank() } ?: sha256("govuk:$url")

        val publicTimestamp = result.publicTimestamp?.let { parseTimestamp(it) } ?: return null

        // Only include items whose public_timestamp falls within today's window.
        if (publicTimestamp < window.start || publicTimestamp >= window.end) return null

        val firstPublishedAt = result.firstPublishedAt?.let { parseTimestamp(it) }

        // If first_published_at is available and earlier than public_timestamp,
        // treat public_timestamp as the update time. Otherwise we have no update info.
        val publishedAt = firstPublishedAt ?: publicTimestamp
        val updatedAt   = if (firstPublishedAt != null && publicTimestamp != firstPublishedAt) publicTimestamp else null

        val tags = buildTags(result)

        return ItemRow(
            id          = id,
            source      = "govuk",
            title       = result.title?.ifBlank { null } ?: "Untitled",
            url         = url,
            publishedAt = publishedAt,
            updatedAt   = updatedAt,
            tags        = tags,
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
