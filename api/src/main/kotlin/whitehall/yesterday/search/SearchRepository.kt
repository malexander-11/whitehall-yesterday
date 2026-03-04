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
     *  1. Lexical: ts_rank_cd over search_tsv for each query in [lexicalQueries], results unioned (max score per ID)
     *  2. Semantic: cosine similarity over embedding (runs if queryEmbedding != null)
     *  3. Reciprocal Rank Fusion (RRF, k=60) to merge candidate lists
     *  4. Title boost applied within the result page (full phrase = +0.05, all tokens = +0.02)
     *  5. Return top [limit] items starting at [offset]
     *
     * Gracefully degrades to lexical-only when queryEmbedding is null (e.g. Voyage unavailable).
     */
    fun search(
        date: LocalDate,
        displayQuery: String,
        lexicalQueries: List<String>,
        queryEmbedding: FloatArray?,
        limit: Int,
        offset: Int
    ): List<IndexItem> {
        val lexScores: Map<String, Double> = runLexicalQuery(date, lexicalQueries)
        val semScores: Map<String, Double> = if (queryEmbedding != null)
            runSemanticQuery(date, queryEmbedding)
        else
            emptyMap()

        val allIds = (lexScores.keys + semScores.keys).toSet()
        if (allIds.isEmpty()) return emptyList()

        // Reciprocal Rank Fusion (Cormack & Clarke 2009, k=60)
        val lexRanks = lexScores.entries.sortedByDescending { it.value }
            .mapIndexed { idx, (id, _) -> id to (idx + 1) }.toMap()
        val semRanks = semScores.entries.sortedByDescending { it.value }
            .mapIndexed { idx, (id, _) -> id to (idx + 1) }.toMap()
        val k = 60
        val rrfScores: Map<String, Double> = allIds.associateWith { id ->
            val lexRrf = lexRanks[id]?.let { 1.0 / (k + it) } ?: 0.0
            val semRrf = semRanks[id]?.let { 1.0 / (k + it) } ?: 0.0
            lexRrf + semRrf
        }

        val rankedIds = allIds.sortedByDescending { rrfScores[it]!! }
        val pageIds = rankedIds.drop(offset).take(limit)
        if (pageIds.isEmpty()) return emptyList()

        val itemsById = fetchItemsByIds(date, pageIds)
        val pageItems = pageIds.mapNotNull { itemsById[it] }

        return applyTitleBoost(pageItems, rrfScores, displayQuery)
    }

    /**
     * Runs one lexical query per entry in [queries], unions results (max score per ID).
     * Multiple queries implement OR semantics for acronym/synonym expansion.
     */
    private fun runLexicalQuery(date: LocalDate, queries: List<String>): Map<String, Double> {
        val allScores = mutableMapOf<String, Double>()
        for (query in queries) {
            runSingleLexicalQuery(date, query).forEach { (id, score) ->
                allScores[id] = maxOf(allScores.getOrDefault(id, 0.0), score)
            }
        }
        return allScores
    }

    private fun runSingleLexicalQuery(date: LocalDate, queryText: String): Map<String, Double> {
        val tsQuery = "plainto_tsquery('english', ?)"
        return jdbc.query(
            """
            SELECT i.id, ts_rank_cd(i.search_tsv, $tsQuery) AS lex_score
            FROM items i
            JOIN daily_index di ON di.canonical_id = i.id
            WHERE di.date = ?
              AND i.search_tsv @@ $tsQuery
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
     * Re-sorts [items] within the page by RRF score + a title-match boost.
     * Boost values are calibrated against the RRF range for 200-result sets (~0.006–0.033):
     *   - Full phrase in title: +0.05 (guarantees top rank for exact matches)
     *   - All query tokens in title: +0.02 (strong boost without overriding semantic relevance)
     */
    private fun applyTitleBoost(
        items: List<IndexItem>,
        rrfScores: Map<String, Double>,
        query: String
    ): List<IndexItem> {
        val q = query.lowercase()
        val tokens = q.split(Regex("\\s+")).filter { it.length > 2 }
        return items.sortedByDescending { item ->
            val title = item.title.lowercase()
            val rrf = rrfScores[item.id] ?: 0.0
            val boost = when {
                title.contains(q) -> 0.05
                tokens.isNotEmpty() && tokens.all { title.contains(it) } -> 0.02
                else -> 0.0
            }
            rrf + boost
        }
    }
}
