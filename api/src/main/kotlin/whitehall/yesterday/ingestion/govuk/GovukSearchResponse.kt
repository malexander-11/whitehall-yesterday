package whitehall.yesterday.ingestion.govuk

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class GovukSearchResponse(
    val results: List<GovukSearchResult> = emptyList(),
    val total: Int = 0,
    val start: Int = 0,
    val count: Int = 0
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GovukSearchResult(
    val title: String? = null,
    val link: String? = null,

    @JsonProperty("public_timestamp")
    val publicTimestamp: String? = null,

    @JsonProperty("first_published_at")
    val firstPublishedAt: String? = null,

    val format: String? = null,

    @JsonProperty("content_id")
    val contentId: String? = null,

    // Array of objects; we only need "slug" and "title" downstream.
    // Using Map to avoid strict coupling to GOV.UK's organisation schema.
    val organisations: List<Map<String, Any?>>? = null,

    val description: String? = null
)
