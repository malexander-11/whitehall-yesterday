package whitehall.yesterday.ingestion.ons

import org.springframework.stereotype.Component
import whitehall.yesterday.ingestion.DateWindow
import whitehall.yesterday.ingestion.ItemRow
import whitehall.yesterday.ingestion.SourceIngester
import java.security.MessageDigest
import java.time.OffsetDateTime

@Component
class OnsSourceIngester(private val client: OnsClient) : SourceIngester {

    override val sourceName = "ons"

    override fun fetchItems(window: DateWindow): List<ItemRow> =
        client.fetchPublishedReleases(window.date).mapNotNull { mapToItemRow(it) }

    private fun mapToItemRow(release: OnsRelease): ItemRow? {
        val desc = release.description ?: return null
        val title = desc.title?.takeIf { it.isNotBlank() } ?: return null
        val publishedAt = desc.releaseDate
            ?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() }
            ?: return null

        return ItemRow(
            id              = sha256("ons:${release.uri}"),
            source          = "ons",
            title           = title,
            url             = "https://www.ons.gov.uk${release.uri}",
            publishedAt     = publishedAt,
            updatedAt       = null,
            tags            = buildTags(desc),
            raw             = release,
            sourceSubtype   = "release",
            sourceReference = release.uri
        )
    }

    private fun buildTags(desc: OnsReleaseDescription) = buildMap<String, Any> {
        put("format", "statistical_release")
        desc.summary?.let { put("summary", it) }
        desc.keywords?.takeIf { it.isNotEmpty() }?.let { put("keywords", it) }
        desc.nationalStatistic?.takeIf { it }?.let { put("national_statistic", true) }
        desc.census?.takeIf { it }?.let { put("census", true) }
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
