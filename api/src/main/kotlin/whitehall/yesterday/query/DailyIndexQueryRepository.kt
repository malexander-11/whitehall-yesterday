package whitehall.yesterday.query

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.time.OffsetDateTime

@Repository
class DailyIndexQueryRepository(
    private val jdbc: JdbcTemplate,
    private val objectMapper: ObjectMapper
) {
    fun findByDate(date: LocalDate): DailyIndexResponse? {
        val items = jdbc.query(
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
            ORDER BY i.source, di.bucket, i.published_at DESC
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
            date
        )

        if (items.isEmpty()) return null

        return DailyIndexResponse(
            date         = date,
            totalCount   = items.size,
            newCount     = items.count { it.bucket == "NEW" },
            updatedCount = items.count { it.bucket == "UPDATED" },
            items        = items
        )
    }
}
