package whitehall.yesterday.ingestion

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * UTC-equivalent Instant boundaries for a calendar day in the Europe/London timezone.
 *
 * Using atStartOfDay(zone) on both ends means the window is always exactly one
 * calendar day in London time — 23h on spring-forward, 24h normally, 25h on fall-back.
 */
data class DateWindow(
    val date: LocalDate,
    val start: Instant,  // inclusive — midnight London time, as UTC Instant
    val end: Instant     // exclusive — midnight of the *next* day London time, as UTC Instant
) {
    companion object {
        private val LONDON = ZoneId.of("Europe/London")

        fun forDate(date: LocalDate): DateWindow {
            val start = date.atStartOfDay(LONDON).toInstant()
            val end   = date.plusDays(1).atStartOfDay(LONDON).toInstant()
            return DateWindow(date, start, end)
        }
    }
}
