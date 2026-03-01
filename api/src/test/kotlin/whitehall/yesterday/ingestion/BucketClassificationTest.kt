package whitehall.yesterday.ingestion

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDate

class BucketClassificationTest {

    // Window for 2026-02-27 in winter (GMT = UTC, so midnight-to-midnight UTC)
    private val date   = LocalDate.of(2026, 2, 27)
    private val window = DateWindow.forDate(date)

    private fun ts(s: String) = java.time.Instant.parse(s)

    @Test
    fun `published within day is NEW`() {
        val result = classifyBucket(
            publishedAt = ts("2026-02-27T10:00:00Z"),
            updatedAt   = null,
            window      = window
        )
        assertEquals(Bucket.NEW, result)
    }

    @Test
    fun `published before day, updated within day is UPDATED`() {
        val result = classifyBucket(
            publishedAt = ts("2026-02-01T09:00:00Z"),
            updatedAt   = ts("2026-02-27T14:00:00Z"),
            window      = window
        )
        assertEquals(Bucket.UPDATED, result)
    }

    @Test
    fun `published within day and updated within day resolves to NEW`() {
        // When both timestamps fall within the day, publishedAt check wins (NEW).
        // IngestionService nulls out updatedAt when it equals publicTimestamp,
        // so this test covers the case where updatedAt remains non-null but pub is also in window.
        val result = classifyBucket(
            publishedAt = ts("2026-02-27T10:00:00Z"),
            updatedAt   = ts("2026-02-27T12:00:00Z"),
            window      = window
        )
        assertEquals(Bucket.NEW, result)
    }

    @Test
    fun `published and updated both before the day are excluded`() {
        val result = classifyBucket(
            publishedAt = ts("2026-02-25T10:00:00Z"),
            updatedAt   = ts("2026-02-26T10:00:00Z"),
            window      = window
        )
        assertNull(result)
    }

    @Test
    fun `published after the day is excluded`() {
        val result = classifyBucket(
            publishedAt = ts("2026-02-28T00:00:00Z"), // exactly at end boundary (exclusive)
            updatedAt   = null,
            window      = window
        )
        assertNull(result)
    }

    @Test
    fun `updated within day but published also within day still returns NEW not UPDATED`() {
        // Published at start of window, updated at end — publishedAt check fires first.
        val result = classifyBucket(
            publishedAt = ts("2026-02-27T00:00:00Z"),
            updatedAt   = ts("2026-02-27T23:59:59Z"),
            window      = window
        )
        assertEquals(Bucket.NEW, result)
    }

    @Test
    fun `published exactly at window start is NEW`() {
        val result = classifyBucket(
            publishedAt = ts("2026-02-27T00:00:00Z"), // inclusive
            updatedAt   = null,
            window      = window
        )
        assertEquals(Bucket.NEW, result)
    }
}
