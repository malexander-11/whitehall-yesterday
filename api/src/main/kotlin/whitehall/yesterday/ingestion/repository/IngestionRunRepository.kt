package whitehall.yesterday.ingestion.repository

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.util.UUID

@Repository
class IngestionRunRepository(
    private val jdbc: JdbcTemplate,
    private val objectMapper: ObjectMapper
) {
    /** Opens a new run record with status RUNNING. Returns the new run id. */
    fun create(date: LocalDate): UUID {
        val id = UUID.randomUUID()
        jdbc.update(
            """
            INSERT INTO ingestion_runs (id, date, started_at, status)
            VALUES (?::uuid, ?, now(), 'RUNNING'::run_status_enum)
            """.trimIndent(),
            id.toString(), date
        )
        return id
    }

    fun markSuccess(runId: UUID, totalCount: Int, sourceCounts: Map<String, Int>) {
        val countsJson = objectMapper.writeValueAsString(sourceCounts)
        jdbc.update(
            """
            UPDATE ingestion_runs
            SET finished_at   = now(),
                status        = 'SUCCESS'::run_status_enum,
                total_count   = ?,
                source_counts = ?::jsonb
            WHERE id = ?::uuid
            """.trimIndent(),
            totalCount, countsJson, runId.toString()
        )
    }

    fun markFailed(runId: UUID, errorSummary: String) {
        jdbc.update(
            """
            UPDATE ingestion_runs
            SET finished_at   = now(),
                status        = 'FAILED'::run_status_enum,
                error_summary = ?
            WHERE id = ?::uuid
            """.trimIndent(),
            errorSummary, runId.toString()
        )
    }
}
