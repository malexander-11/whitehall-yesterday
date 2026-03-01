package whitehall.yesterday.ingestion

import java.time.Instant

/**
 * Classifies an item into a bucket for a given date window.
 *
 * Rules (exclusive end boundary — see DateWindow):
 *   published_at in [start, end)                          → NEW
 *   updated_at in [start, end) AND published_at < start   → UPDATED
 *   otherwise                                             → null (exclude)
 *
 * "Both published and updated on the same day" resolves to NEW because the
 * updated_at will have been nulled out upstream (equal to published_at).
 */
fun classifyBucket(publishedAt: Instant, updatedAt: Instant?, window: DateWindow): Bucket? =
    when {
        publishedAt >= window.start && publishedAt < window.end ->
            Bucket.NEW
        updatedAt != null
                && updatedAt >= window.start
                && updatedAt < window.end
                && publishedAt < window.start ->
            Bucket.UPDATED
        else -> null
    }
