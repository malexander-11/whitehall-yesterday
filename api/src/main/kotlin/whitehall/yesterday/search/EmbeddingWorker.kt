package whitehall.yesterday.search

import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class EmbeddingWorker(
    private val jdbc: JdbcTemplate,
    private val embeddingService: EmbeddingService
) {
    private val log = LoggerFactory.getLogger(EmbeddingWorker::class.java)

    /**
     * Generates and stores embeddings for all items in the daily index that lack one.
     * Runs asynchronously after ingestion completes — failure does not affect item visibility.
     * Text corpus matches search_tsv: title + description (from tags).
     */
    @Async
    fun generateForDate(date: LocalDate) {
        log.info("Embedding generation starting for $date")
        try {
            val rows = fetchItemsWithoutEmbeddings(date)
            if (rows.isEmpty()) {
                log.info("No items needing embeddings for $date")
                return
            }
            log.info("Generating embeddings for ${rows.size} items on $date")

            val texts = rows.map { (_, title, description) ->
                if (description.isNullOrBlank()) title else "$title $description"
            }

            val embeddings = embeddingService.embed(texts)
            if (embeddings.isEmpty()) {
                log.warn("Embedding service returned no results for $date — skipping")
                return
            }

            var saved = 0
            rows.zip(embeddings).forEach { (row, embedding) ->
                val (id) = row
                val vectorLiteral = embedding.joinToString(",", "[", "]")
                jdbc.update("UPDATE items SET embedding = ?::vector WHERE id = ?", vectorLiteral, id)
                saved++
            }
            log.info("Saved $saved embeddings for $date")
        } catch (e: Exception) {
            log.error("Embedding generation failed for $date: ${e.message}", e)
        }
    }

    /**
     * Backfills embeddings for up to [limit] items globally (no date filter).
     * Capped at 1000 to prevent excessive memory/network use per call.
     * Returns the number of items processed and failed.
     */
    fun backfill(limit: Int): BackfillResult {
        val cap = limit.coerceIn(1, MAX_BACKFILL)
        val rows = jdbc.query(
            "SELECT id, title, tags->>'description' AS description FROM items WHERE embedding IS NULL LIMIT ?",
            { rs, _ -> Triple(rs.getString("id"), rs.getString("title"), rs.getString("description")) },
            cap
        )
        if (rows.isEmpty()) return BackfillResult(processed = 0, failed = 0)

        val texts = rows.map { (_, title, description) ->
            if (description.isNullOrBlank()) title else "$title $description"
        }
        val embeddings = embeddingService.embed(texts)
        if (embeddings.isEmpty()) return BackfillResult(processed = 0, failed = rows.size)

        var saved = 0
        var failed = 0
        rows.zip(embeddings).forEach { (row, embedding) ->
            val (id) = row
            try {
                val vectorLiteral = embedding.joinToString(",", "[", "]")
                jdbc.update("UPDATE items SET embedding = ?::vector WHERE id = ?", vectorLiteral, id)
                saved++
            } catch (e: Exception) {
                log.error("Failed to save embedding for item {}: {}", id, e.message)
                failed++
            }
        }
        log.info("Backfill complete: processed={} failed={}", saved, failed)
        return BackfillResult(processed = saved, failed = failed)
    }

    private fun fetchItemsWithoutEmbeddings(date: LocalDate): List<Triple<String, String, String?>> =
        jdbc.query(
            """
            SELECT i.id, i.title, i.tags->>'description' AS description
            FROM items i
            JOIN daily_index di ON di.canonical_id = i.id
            WHERE di.date = ? AND i.embedding IS NULL
            """.trimIndent(),
            { rs, _ -> Triple(rs.getString("id"), rs.getString("title"), rs.getString("description")) },
            date
        )

    companion object {
        private const val MAX_BACKFILL = 1000
    }
}

data class BackfillResult(val processed: Int, val failed: Int)
