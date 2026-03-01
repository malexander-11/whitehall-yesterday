package whitehall.yesterday.ingestion.repository

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import whitehall.yesterday.ingestion.ItemRow
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Repository
class ItemRepository(
    private val jdbc: JdbcTemplate,
    private val objectMapper: ObjectMapper
) {
    /**
     * Upserts an item by canonical_id.
     * On conflict: updates title, timestamps, raw payload, and merges tags (JSONB union).
     * published_at is preserved as the earliest known value; created_at is never overwritten.
     */
    fun upsert(item: ItemRow) {
        val publishedAt = OffsetDateTime.ofInstant(item.publishedAt, ZoneOffset.UTC)
        val updatedAt   = item.updatedAt?.let { OffsetDateTime.ofInstant(it, ZoneOffset.UTC) }
        val tagsJson    = objectMapper.writeValueAsString(item.tags)
        val rawJson     = objectMapper.writeValueAsString(item.raw)

        jdbc.update(
            """
            INSERT INTO items (id, source, title, url, published_at, updated_at, tags, raw, created_at, modified_at)
            VALUES (?, ?::source_enum, ?, ?, ?, ?, ?::jsonb, ?::jsonb, now(), now())
            ON CONFLICT (id) DO UPDATE SET
                title        = EXCLUDED.title,
                published_at = LEAST(items.published_at, EXCLUDED.published_at),
                updated_at   = EXCLUDED.updated_at,
                raw          = EXCLUDED.raw,
                tags         = items.tags || EXCLUDED.tags,
                modified_at  = now()
            """.trimIndent(),
            item.id, item.source, item.title, item.url,
            publishedAt, updatedAt,
            tagsJson, rawJson
        )
    }

    /**
     * Returns the subset of [ids] that already existed in the database before [before].
     * Used by IngestionService to classify items as UPDATED (previously known) vs NEW.
     */
    fun existingIds(ids: List<String>, before: Instant): Set<String> {
        if (ids.isEmpty()) return emptySet()
        val beforeOdt = OffsetDateTime.ofInstant(before, ZoneOffset.UTC)
        val placeholders = ids.joinToString(",") { "?" }
        val args: Array<Any> = (ids.toList<Any>() + beforeOdt).toTypedArray()
        return jdbc.query(
            "SELECT id FROM items WHERE id IN ($placeholders) AND created_at < ?",
            { rs, _ -> rs.getString("id") },
            *args
        ).toSet()
    }
}
