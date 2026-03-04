package whitehall.yesterday.search

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class EmbeddingWorker(
    private val jdbc: JdbcTemplate,
    private val embeddingService: EmbeddingService,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(EmbeddingWorker::class.java)

    /**
     * Generates and stores embeddings for all items in the daily index that lack one.
     * Runs asynchronously after ingestion completes — failure does not affect item visibility.
     *
     * Corpus: title + description (if present) + organisation names (slug → human-readable form).
     * Example: "NHS Workforce Statistics nhs england" — gives richer semantic context than title alone.
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

            val texts = rows.map { buildText(it) }
            val embeddings = embeddingService.embed(texts)
            if (embeddings.isEmpty()) {
                log.warn("Embedding service returned no results for $date — skipping")
                return
            }

            var saved = 0
            rows.zip(embeddings).forEach { (row, embedding) ->
                val vectorLiteral = embedding.joinToString(",", "[", "]")
                jdbc.update("UPDATE items SET embedding = ?::vector WHERE id = ?", vectorLiteral, row.id)
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
            """
            SELECT id, title,
                   tags->>'description'  AS description,
                   tags->'organisations' AS organisations_json
            FROM items
            WHERE embedding IS NULL
            LIMIT ?
            """.trimIndent(),
            { rs, _ -> ItemRow(
                id               = rs.getString("id"),
                title            = rs.getString("title"),
                description      = rs.getString("description"),
                organisationsJson = rs.getString("organisations_json")
            )},
            cap
        )
        if (rows.isEmpty()) return BackfillResult(processed = 0, failed = 0)

        val texts = rows.map { buildText(it) }
        val embeddings = embeddingService.embed(texts)
        if (embeddings.isEmpty()) return BackfillResult(processed = 0, failed = rows.size)

        var saved = 0
        var failed = 0
        rows.zip(embeddings).forEach { (row, embedding) ->
            try {
                val vectorLiteral = embedding.joinToString(",", "[", "]")
                jdbc.update("UPDATE items SET embedding = ?::vector WHERE id = ?", vectorLiteral, row.id)
                saved++
            } catch (e: Exception) {
                log.error("Failed to save embedding for item {}: {}", row.id, e.message)
                failed++
            }
        }
        log.info("Backfill complete: processed={} failed={}", saved, failed)
        return BackfillResult(processed = saved, failed = failed)
    }

    private fun fetchItemsWithoutEmbeddings(date: LocalDate): List<ItemRow> =
        jdbc.query(
            """
            SELECT i.id, i.title,
                   i.tags->>'description'  AS description,
                   i.tags->'organisations' AS organisations_json
            FROM items i
            JOIN daily_index di ON di.canonical_id = i.id
            WHERE di.date = ? AND i.embedding IS NULL
            """.trimIndent(),
            { rs, _ -> ItemRow(
                id                = rs.getString("id"),
                title             = rs.getString("title"),
                description       = rs.getString("description"),
                organisationsJson = rs.getString("organisations_json")
            )},
            date
        )

    /**
     * Builds the text string used for embedding.
     * Corpus: title + description (if present) + organisation slugs converted to readable form.
     * "hm-treasury" → "hm treasury" (hyphen → space; embedding model handles the rest).
     */
    private fun buildText(row: ItemRow): String {
        val orgs = row.organisationsJson
            ?.let { runCatching { objectMapper.readTree(it) }.getOrNull() }
            ?.joinToString(" ") { it.asText().replace('-', ' ') }
            .orEmpty()
        return buildString {
            append(row.title)
            if (!row.description.isNullOrBlank()) { append(' '); append(row.description) }
            if (orgs.isNotBlank()) { append(' '); append(orgs) }
        }
    }

    private data class ItemRow(
        val id: String,
        val title: String,
        val description: String?,
        val organisationsJson: String?
    )

    companion object {
        private const val MAX_BACKFILL = 1000
    }
}

data class BackfillResult(val processed: Int, val failed: Int)
