package whitehall.yesterday.ingestion.govuk

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import whitehall.yesterday.ingestion.DateWindow
import java.net.URI
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@Component
class GovukClient(private val govukRestClient: RestClient) {

    private val log = LoggerFactory.getLogger(GovukClient::class.java)

    companion object {
        private const val PAGE_SIZE = 100
        private const val MAX_RETRIES = 3
        private const val BASE_DELAY_MS = 1_000L
        private val FIELDS = listOf(
            "title", "link", "public_timestamp", "first_published_at",
            "format", "organisations", "content_id", "description"
        )
    }

    /**
     * Fetches all results relevant to the given date window.
     *
     * The GOV.UK Search API does not support date-range filters, so we fetch
     * pages ordered by -public_timestamp (newest first) and stop as soon as
     * the oldest item on a page predates our window.  Items outside the window
     * are passed back as-is; IngestionService / BucketClassifier will exclude them.
     *
     * Throws if any page fails after MAX_RETRIES attempts — the caller should
     * treat this as a FAILED run and leave daily_index untouched.
     */
    fun fetchAll(window: DateWindow): List<GovukSearchResult> {
        val all = mutableListOf<GovukSearchResult>()
        var start = 0
        var apiTotal: Int? = null

        do {
            val page = fetchPageWithRetry(start)
            if (apiTotal == null) apiTotal = page.total
            all += page.results
            start += page.results.size

            // Items are ordered newest-first.  Once the oldest on a page predates
            // our window, all subsequent pages will also be before the window — stop.
            val oldestOnPage = page.results
                .mapNotNull { parseInstantOrNull(it.publicTimestamp) }
                .minOrNull()
            val windowExhausted = oldestOnPage != null && oldestOnPage < window.start

            log.debug(
                "Page start={} results={} oldest={} windowExhausted={}",
                start, page.results.size, oldestOnPage, windowExhausted
            )

            if (windowExhausted) break
        } while (page.results.isNotEmpty() && start < (apiTotal ?: Int.MAX_VALUE))

        log.info("GOV.UK fetch complete: {} raw results (API total={})", all.size, apiTotal)
        return all
    }

    private fun parseInstantOrNull(s: String?): Instant? =
        if (s == null) null
        else try { OffsetDateTime.parse(s).toInstant() }
        catch (_: Exception) { try { LocalDate.parse(s).atStartOfDay(ZoneOffset.UTC).toInstant() } catch (_: Exception) { null } }

    private fun fetchPageWithRetry(start: Int): GovukSearchResponse {
        var lastException: Exception? = null
        var delayMs = BASE_DELAY_MS

        repeat(MAX_RETRIES) { attempt ->
            try {
                return fetchPage(start)
            } catch (e: HttpClientErrorException.TooManyRequests) {
                val waitMs = parseRetryAfterMs(e.responseHeaders?.getFirst("Retry-After"))
                log.warn("429 rate-limited. Waiting {}ms (attempt {}/{})", waitMs, attempt + 1, MAX_RETRIES)
                Thread.sleep(waitMs)
                lastException = e
            } catch (e: HttpClientErrorException) {
                // Other 4xx errors are not retryable (e.g. 404, 400)
                throw e
            } catch (e: Exception) {
                lastException = e
                log.warn("GOV.UK fetch failed (attempt {}/{}): {}", attempt + 1, MAX_RETRIES, e.message)
                if (attempt < MAX_RETRIES - 1) {
                    val jitter = 0.8 + Math.random() * 0.4   // ±20%
                    Thread.sleep((delayMs * jitter).toLong())
                    delayMs *= 2
                }
            }
        }

        throw lastException ?: RuntimeException("GOV.UK fetch failed after $MAX_RETRIES attempts")
    }

    private fun fetchPage(start: Int): GovukSearchResponse {
        val uri = buildUri(start)
        return govukRestClient.get()
            .uri(uri)
            .retrieve()
            .body(GovukSearchResponse::class.java)
            ?: GovukSearchResponse()
    }

    private fun buildUri(start: Int): URI {
        // No server-side date filter — GOV.UK Search API does not support it.
        // Fetch newest-first; fetchAll() stops once items go past our window.
        val fields = FIELDS.joinToString("&") { "fields%5B%5D=$it" }
        return URI.create(
            "https://www.gov.uk/api/search.json" +
                "?count=$PAGE_SIZE&start=$start&order=-public_timestamp&$fields"
        )
    }

    /**
     * Parses a Retry-After header value.
     * Accepts either a number of seconds (e.g. "30") or an HTTP-date (RFC 1123).
     * Falls back to 5 seconds if unparseable.
     */
    private fun parseRetryAfterMs(value: String?): Long {
        if (value == null) return 5_000L
        val seconds = value.toLongOrNull()
        if (seconds != null) return seconds * 1_000L
        return try {
            val date = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()
            (date.toEpochMilli() - Instant.now().toEpochMilli()).coerceAtLeast(0L)
        } catch (_: Exception) {
            5_000L
        }
    }
}
