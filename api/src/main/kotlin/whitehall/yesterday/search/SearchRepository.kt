package whitehall.yesterday.search

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import whitehall.yesterday.query.IndexItem
import java.time.LocalDate
import java.time.OffsetDateTime

@Repository
class SearchRepository(
    private val jdbc: JdbcTemplate,
    private val objectMapper: ObjectMapper
) {
    /**
     * Hybrid search over items indexed for [date].
     *
     * Strategy:
     *  1. Lexical: ts_rank_cd over search_tsv (always runs)
     *  2. Semantic: cosine similarity over embedding (runs if queryEmbedding != null)
     *  3. Union candidates, normalise scores 0–1, apply 0.6 * sem + 0.4 * lex weighting
     *  4. Return top [limit] items starting at [offset], ranked by final_score
     *
     * Gracefully degrades to lexical-only when queryEmbedding is null (e.g. Voyage unavailable).
     */
    fun search(
        date: LocalDate,
        queryText: String,
        queryEmbedding: FloatArray?,
        limit: Int,
        offset: Int
    ): List<IndexItem> {
        val lexScores: Map<String, Double> = runLexicalQuery(date, queryText)
        val semScores: Map<String, Double> = if (queryEmbedding != null)
            runSemanticQuery(date, queryEmbedding)
        else
            emptyMap()

        val allIds = (lexScores.keys + semScores.keys).toSet()
        if (allIds.isEmpty()) return emptyList()

        val normLex = normalise(lexScores)
        val normSem = normalise(semScores)

        val ranked = allIds
            .map { id ->
                val score = 0.6 * (normSem[id] ?: 0.0) + 0.4 * (normLex[id] ?: 0.0)
                id to score
            }
            .sortedByDescending { (_, score) -> score }

        val pageIds = ranked.drop(offset).take(limit).map { (id, _) -> id }
        if (pageIds.isEmpty()) return emptyList()

        val itemsByid = fetchItemsByIds(date, pageIds)
        // Restore rank order
        return pageIds.mapNotNull { itemsByid[it] }
    }

    private fun runLexicalQuery(date: LocalDate, queryText: String): Map<String, Double> {
        val placeholders = "plainto_tsquery('english', ?)"
        return jdbc.query(
            """
            SELECT i.id, ts_rank_cd(i.search_tsv, $placeholders) AS lex_score
            FROM items i
            JOIN daily_index di ON di.canonical_id = i.id
            WHERE di.date = ?
              AND i.search_tsv @@ $placeholders
            ORDER BY lex_score DESC
            LIMIT 200
            """.trimIndent(),
            { rs, _ -> rs.getString("id") to rs.getDouble("lex_score") },
            queryText, date, queryText
        ).toMap()
    }

    private fun runSemanticQuery(date: LocalDate, embedding: FloatArray): Map<String, Double> {
        val vectorLiteral = embedding.joinToString(",", "[", "]")
        return jdbc.query(
            """
            SELECT i.id, 1 - (i.embedding <=> ?::vector) AS sem_score
            FROM items i
            JOIN daily_index di ON di.canonical_id = i.id
            WHERE di.date = ?
              AND i.embedding IS NOT NULL
            ORDER BY i.embedding <=> ?::vector
            LIMIT 200
            """.trimIndent(),
            { rs, _ -> rs.getString("id") to rs.getDouble("sem_score") },
            vectorLiteral, date, vectorLiteral
        ).toMap()
    }

    private fun fetchItemsByIds(date: LocalDate, ids: List<String>): Map<String, IndexItem> {
        if (ids.isEmpty()) return emptyMap()
        val placeholders = ids.joinToString(",") { "?" }
        val args: Array<Any> = (listOf<Any>(date) + ids).toTypedArray()
        return jdbc.query(
            """
            SELECT
                di.bucket,
                i.id,
                i.title,
                i.url,
                i.published_at,
                i.updated_at,
                i.source,
                i.source_subtype,
                i.source_reference,
                i.tags->>'format'        AS format,
                i.tags->'organisations'  AS organisations_json
            FROM daily_index di
            JOIN items i ON i.id = di.canonical_id
            WHERE di.date = ?
              AND i.id IN ($placeholders)
            """.trimIndent(),
            { rs, _ ->
                val orgsJson = rs.getString("organisations_json")
                val orgs: List<String> = if (orgsJson != null)
                    objectMapper.readValue(orgsJson, object : TypeReference<List<String>>() {})
                else emptyList()

                IndexItem(
                    id              = rs.getString("id"),
                    bucket          = rs.getString("bucket"),
                    title           = rs.getString("title"),
                    url             = rs.getString("url"),
                    publishedAt     = rs.getObject("published_at", OffsetDateTime::class.java).toInstant(),
                    updatedAt       = rs.getObject("updated_at", OffsetDateTime::class.java)?.toInstant(),
                    format          = rs.getString("format"),
                    organisations   = orgs,
                    source          = rs.getString("source"),
                    sourceSubtype   = rs.getString("source_subtype"),
                    sourceReference = rs.getString("source_reference")
                )
            },
            *args
        ).associateBy { it.id }
    }

    /**
     * Min-max normalises scores to [0, 1].
     * When max == min (all identical, or single result), returns 1.0 for all to avoid division-by-zero.
     */
    private fun normalise(scores: Map<String, Double>): Map<String, Double> {
        if (scores.isEmpty()) return emptyMap()
        val min = scores.values.min()
        val max = scores.values.max()
        return if (max == min) {
            scores.mapValues { 1.0 }
        } else {
            scores.mapValues { (_, v) -> (v - min) / (max - min) }
        }
    }
}
