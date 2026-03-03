package whitehall.yesterday.ingestion.ons

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.net.URI
import java.time.LocalDate

@Component
class OnsClient(@Qualifier("ons") private val client: RestClient) {

    fun fetchPublishedReleases(date: LocalDate): List<OnsRelease> {
        val d = date.toString()
        val response = client.get()
            .uri(URI.create(
                "https://api.beta.ons.gov.uk/v1/search/releases" +
                "?fromDate=$d&toDate=$d&release-type=type-published&limit=200"
            ))
            .retrieve()
            .body(OnsReleasesResponse::class.java)
            ?: OnsReleasesResponse()

        return response.releases.orEmpty().filter {
            it.description?.published == true && it.description.cancelled != true
        }
    }
}
