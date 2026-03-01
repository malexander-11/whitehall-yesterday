package whitehall.yesterday.ingestion.repository

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import whitehall.yesterday.ingestion.Bucket
import java.time.LocalDate

@Repository
class DailyIndexRepository(private val jdbc: JdbcTemplate) {

    /**
     * Atomically rebuilds the daily index for the given date.
     * DELETE + batch INSERT run in a single transaction so the index
     * is never partially updated — either the whole day is replaced or nothing changes.
     */
    @Transactional
    fun rebuild(date: LocalDate, rows: List<Pair<String, Bucket>>) {
        jdbc.update("DELETE FROM daily_index WHERE date = ?", date)

        if (rows.isNotEmpty()) {
            jdbc.batchUpdate(
                "INSERT INTO daily_index (date, canonical_id, bucket) VALUES (?, ?, ?::bucket_enum)",
                rows.map { (id, bucket) -> arrayOf(date, id, bucket.name) }
            )
        }
    }
}
