package whitehall.yesterday.ingestion.ons

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming

data class OnsReleasesResponse(
    val releases: List<OnsRelease>? = null
)

data class OnsRelease(
    val uri: String,
    val description: OnsReleaseDescription?
)

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class OnsReleaseDescription(
    val title: String?,
    val summary: String?,
    val releaseDate: String?,        // ISO 8601: "2026-02-19T07:00:00.000Z"
    val published: Boolean = false,
    val cancelled: Boolean = false,
    val keywords: List<String>?,
    val nationalStatistic: Boolean?,
    val census: Boolean?
)
