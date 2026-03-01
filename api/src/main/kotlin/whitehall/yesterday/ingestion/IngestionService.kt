package whitehall.yesterday.ingestion

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import whitehall.yesterday.ingestion.repository.DailyIndexRepository
import whitehall.yesterday.ingestion.repository.IngestionRunRepository
import whitehall.yesterday.ingestion.repository.ItemRepository
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

@Service
class IngestionService(
    private val ingesters: List<SourceIngester>,
    private val itemRepository: ItemRepository,
    private val dailyIndexRepository: DailyIndexRepository,
    private val ingestionRunRepository: IngestionRunRepository
) {
    private val log = LoggerFactory.getLogger(IngestionService::class.java)

    /**
     * Runs a full ingest for [date] across all registered sources and returns the result.
     *
     * Lifecycle:
     *  1. Open run (status = RUNNING). If one is already RUNNING or SUCCESS, return early.
     *  2. Call each [SourceIngester] to fetch items for the date window.
     *  3. Classify GOV.UK items as NEW/UPDATED via DB lookup; Parliament items are always NEW.
     *  4. Upsert all items.
     *  5. Atomically rebuild daily_index for [date].
     *  6. Mark run SUCCESS with per-source counts.
     */
    fun ingest(date: LocalDate): IngestionRunResult {
        val window = DateWindow.forDate(date)
        log.info("Ingestion started date={} window=[{}, {})", date, window.start, window.end)

        // Run-claim: skip if a run is already in progress or succeeded for this date
        val existingRun = ingestionRunRepository.findActiveRun(date)
        if (existingRun != null) {
            log.info("Ingestion skipped date={} — existing run id={} status={}", date, existingRun.first, existingRun.second)
            return IngestionRunResult(existingRun.first, date, existingRun.second, 0, emptyMap(), 0)
        }

        val runId = ingestionRunRepository.create(date)
        val startedAt = Instant.now()

        return try {
            val allItems = mutableListOf<ItemRow>()
            val sourceCounts = mutableMapOf<String, Int>()

            for (ingester in ingesters) {
                val items = ingester.fetchItems(window)
                allItems += items
                sourceCounts[ingester.sourceName] = items.size
                log.info("Source {} fetched {} items for date={}", ingester.sourceName, items.size, date)
            }

            // Bucket classification:
            //   - GOV.UK: DB-based (items seen before this window → UPDATED, others → NEW)
            //   - Parliament: always NEW per spec
            val govukItems = allItems.filter { it.source == "govuk" }
            val parliamentItems = allItems.filter { it.source == "parliament" }

            val existingIds = if (govukItems.isEmpty()) emptySet()
                              else itemRepository.existingIds(govukItems.map { it.id }, window.start)

            val itemsWithBuckets: List<Pair<ItemRow, Bucket>> = govukItems.map { item ->
                item to if (item.id in existingIds) Bucket.UPDATED else Bucket.NEW
            } + parliamentItems.map { item ->
                item to Bucket.NEW
            }

            log.info(
                "Classified {} items for date={} (new={}, updated={})",
                allItems.size, date,
                itemsWithBuckets.count { (_, b) -> b == Bucket.NEW },
                itemsWithBuckets.count { (_, b) -> b == Bucket.UPDATED }
            )

            itemsWithBuckets.forEach { (item, _) -> itemRepository.upsert(item) }

            dailyIndexRepository.rebuild(date, itemsWithBuckets.map { (item, bucket) -> item.id to bucket })

            val totalCount = allItems.size
            ingestionRunRepository.markSuccess(runId, totalCount, sourceCounts)

            val durationMs = Duration.between(startedAt, Instant.now()).toMillis()
            log.info("Ingestion complete date={} total={} sources={} durationMs={}", date, totalCount, sourceCounts, durationMs)

            IngestionRunResult(runId, date, "SUCCESS", totalCount, sourceCounts, durationMs)
        } catch (e: Exception) {
            val durationMs = Duration.between(startedAt, Instant.now()).toMillis()
            log.error("Ingestion failed date={} durationMs={}", date, durationMs, e)
            ingestionRunRepository.markFailed(runId, e.message ?: "Unknown error")
            IngestionRunResult(runId, date, "FAILED", 0, emptyMap(), durationMs, e.message)
        }
    }
}
