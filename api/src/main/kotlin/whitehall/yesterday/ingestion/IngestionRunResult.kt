package whitehall.yesterday.ingestion

import java.time.LocalDate
import java.util.UUID

/** Response body returned by POST /v1/ingest/{date}. */
data class IngestionRunResult(
    val runId: UUID,
    val date: LocalDate,
    val status: String,
    val totalCount: Int,
    val sourceCounts: Map<String, Int>,
    val durationMs: Long,
    val errorMessage: String? = null
)
