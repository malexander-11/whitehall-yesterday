package whitehall.yesterday.ops

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime

data class RunSummary(
    val id: String,
    val date: LocalDate,
    val status: String,
    val totalCount: Int,
    val sourceCounts: Map<String, Int>,
    val startedAt: Instant,
    val finishedAt: Instant?,
    val errorSummary: String?
)

data class OpsRunsResponse(
    val runs: List<RunSummary>
)

@Repository
class OpsRepository(
    private val jdbc: JdbcTemplate,
    private val objectMapper: ObjectMapper
) {
    fun recentRuns(limit: Int = 30): List<RunSummary> =
        jdbc.query(
            """
            SELECT id, date, status, total_count, source_counts, started_at, finished_at, error_summary
            FROM ingestion_runs
            ORDER BY started_at DESC
            LIMIT ?
            """.trimIndent(),
            { rs, _ ->
                val countsJson = rs.getString("source_counts")
                val counts: Map<String, Int> = objectMapper.readValue(countsJson, object : TypeReference<Map<String, Int>>() {})
                RunSummary(
                    id           = rs.getString("id"),
                    date         = rs.getObject("date", LocalDate::class.java),
                    status       = rs.getString("status"),
                    totalCount   = rs.getInt("total_count"),
                    sourceCounts = counts,
                    startedAt    = rs.getObject("started_at", OffsetDateTime::class.java).toInstant(),
                    finishedAt   = rs.getObject("finished_at", OffsetDateTime::class.java)?.toInstant(),
                    errorSummary = rs.getString("error_summary")
                )
            },
            limit
        )
}

@RestController
@RequestMapping("/ops")
class OpsController(private val repo: OpsRepository) {

    @GetMapping("/runs")
    fun getRuns(): ResponseEntity<OpsRunsResponse> =
        ResponseEntity.ok(OpsRunsResponse(repo.recentRuns()))
}
