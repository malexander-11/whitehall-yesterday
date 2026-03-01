package whitehall.yesterday.ingestion

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.LocalDate

class DateWindowTest {

    @Test
    fun `normal winter day is exactly 24 hours`() {
        // 2026-02-27 — well within GMT (no DST)
        val w = DateWindow.forDate(LocalDate.of(2026, 2, 27))
        assertEquals(Duration.ofHours(24), Duration.between(w.start, w.end))
    }

    @Test
    fun `spring forward day is exactly 23 hours`() {
        // 2026-03-29 — clocks go forward at 01:00 → 02:00, so the day is only 23h
        val w = DateWindow.forDate(LocalDate.of(2026, 3, 29))
        assertEquals(Duration.ofHours(23), Duration.between(w.start, w.end))
    }

    @Test
    fun `fall back day is exactly 25 hours`() {
        // 2025-10-26 — clocks go back at 02:00 → 01:00, so the day is 25h
        val w = DateWindow.forDate(LocalDate.of(2025, 10, 26))
        assertEquals(Duration.ofHours(25), Duration.between(w.start, w.end))
    }

    @Test
    fun `start and end are UTC equivalents of London midnight`() {
        // 2026-02-27 is GMT (UTC+0), so London midnight == UTC midnight
        val w = DateWindow.forDate(LocalDate.of(2026, 2, 27))
        assertEquals("2026-02-27T00:00:00Z", w.start.toString())
        assertEquals("2026-02-28T00:00:00Z", w.end.toString())
    }

    @Test
    fun `summer day start is UTC-1h due to BST`() {
        // 2026-07-15 is BST (UTC+1), so London midnight = 23:00 UTC the day before
        val w = DateWindow.forDate(LocalDate.of(2026, 7, 15))
        assertEquals("2026-07-14T23:00:00Z", w.start.toString())
        assertEquals("2026-07-15T23:00:00Z", w.end.toString())
    }
}
