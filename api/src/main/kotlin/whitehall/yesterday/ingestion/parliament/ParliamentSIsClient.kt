package whitehall.yesterday.ingestion.parliament

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import whitehall.yesterday.ingestion.DateWindow
import whitehall.yesterday.ingestion.ItemRow
import java.security.MessageDigest
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Fetches Statutory Instruments laid before Parliament on a given date.
 *
 * Strategy:
 *  - Use SI API v2 (v1 is deprecated, end-of-support 27 March 2026).
 *  - /api/v2/StatutoryInstrument returns items sorted newest-first by laying date.
 *  - Paginate with Take=200 until all laying dates are before the target date.
 *  - Keep only items where commonsLayingDate OR lordsLayingDate matches target date.
 *  - Safety cap: stop after scanning 1000 items to bound worst-case latency.
 *
 * On a busy sitting day there are typically 20–80 SIs laid.
 */
@Component
class ParliamentSIsClient(
    @Qualifier("parliamentSIs") private val client: RestClient
) {
    private val log = LoggerFactory.getLogger(ParliamentSIsClient::class.java)

    companion object {
        private const val PAGE_SIZE = 200
        private const val MAX_ITEMS = 1000
    }

    fun fetchLaidSIs(window: DateWindow): List<ItemRow> {
        val date = window.date
        val targetDateStr = date.toString()   // YYYY-MM-DD
        log.info("Parliament SIs: fetching SIs laid on date={}", date)

        val collected = mutableListOf<SiSummary>()
        var skip = 0

        while (skip < MAX_ITEMS) {
            val page = fetchPage(skip, PAGE_SIZE)
            val summaries = page.items.mapNotNull { it.value }
            if (summaries.isEmpty()) break

            val relevant = summaries.filter { si ->
                si.commonsLayingDate?.startsWith(targetDateStr) == true ||
                si.lordsLayingDate?.startsWith(targetDateStr) == true
            }
            collected.addAll(relevant)

            // Stop when every item on this page is older than the target date
            val allOlder = summaries.all { si ->
                val earliest = si.commonsLayingDate ?: si.lordsLayingDate
                earliest != null && earliest.take(10) < targetDateStr
            }
            if (allOlder || summaries.size < PAGE_SIZE) break
            skip += PAGE_SIZE
        }

        log.info("Parliament SIs: {} SIs laid on date={} (scanned {} items)", collected.size, date, skip + PAGE_SIZE)
        return collected.mapNotNull { mapToItemRow(it, date) }
    }

    private fun fetchPage(skip: Int, take: Int): SiListResponse =
        client.get()
            .uri("/api/v2/StatutoryInstrument?Skip={skip}&Take={take}", skip, take)
            .retrieve()
            .body(SiListResponse::class.java)
            ?: SiListResponse()

    private fun mapToItemRow(si: SiSummary, date: LocalDate): ItemRow? {
        val workpackageId = si.workpackageId ?: return null
        val tags = buildMap<String, Any?> {
            si.procedure?.name?.let { put("procedure", it) }
            si.paperYear?.let { put("paperYear", it) }
            val ref = listOfNotNull(si.paperPrefix, si.paperNumber?.toString())
            if (ref.isNotEmpty()) put("siReference", ref.joinToString(" "))
        }
        return ItemRow(
            id              = sha256("parliament:si:${si.id}"),
            source          = "parliament",
            title           = si.name?.ifBlank { null } ?: "Untitled SI",
            url             = "https://statutoryinstruments.parliament.uk/instrumentDetail/$workpackageId",
            publishedAt     = date.atStartOfDay(ZoneOffset.UTC).toInstant(),
            updatedAt       = null,
            tags            = tags,
            raw             = si,
            sourceSubtype   = "si",
            sourceReference = "si/${si.id}"
        )
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
