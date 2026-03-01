package whitehall.yesterday.ingestion

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.ZoneId

@Component
class IngestionScheduler(private val ingestionService: IngestionService) {

    private val log = LoggerFactory.getLogger(IngestionScheduler::class.java)

    /**
     * Runs daily at 02:00 Europe/London, targeting the previous calendar day.
     * Any exception is caught and logged — a failed run is already recorded
     * in ingestion_runs by IngestionService.
     */
    @Scheduled(cron = "0 0 2 * * *", zone = "Europe/London")
    fun runDaily() {
        val yesterday = LocalDate.now(ZoneId.of("Europe/London")).minusDays(1)
        log.info("Scheduled ingestion triggered for date={}", yesterday)
        try {
            ingestionService.ingest(yesterday)
        } catch (e: Exception) {
            log.error("Scheduled ingestion threw unexpectedly for date={}", yesterday, e)
        }
    }
}
