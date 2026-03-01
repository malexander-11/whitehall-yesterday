package whitehall.yesterday.ingestion.parliament

import org.springframework.stereotype.Component
import whitehall.yesterday.ingestion.DateWindow
import whitehall.yesterday.ingestion.ItemRow
import whitehall.yesterday.ingestion.SourceIngester

@Component
class ParliamentSiIngester(private val client: ParliamentSIsClient) : SourceIngester {
    override val sourceName = "parliament_si"
    override fun fetchItems(window: DateWindow): List<ItemRow> = client.fetchLaidSIs(window)
}
