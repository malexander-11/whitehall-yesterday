package whitehall.yesterday.ingestion

import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
class IngestionController(private val ingestionService: IngestionService) {

    /**
     * Manually triggers ingestion for a given date. Runs synchronously.
     * Returns 200 on SUCCESS, 500 on FAILED (so callers can detect failure).
     *
     * Example: POST /v1/ingest/2026-02-27
     */
    @PostMapping("/v1/ingest/{date}")
    fun ingest(
        @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate
    ): ResponseEntity<IngestionRunResult> {
        val result = ingestionService.ingest(date)
        val status = if (result.status == "SUCCESS") HttpStatus.OK else HttpStatus.INTERNAL_SERVER_ERROR
        return ResponseEntity.status(status).body(result)
    }
}
