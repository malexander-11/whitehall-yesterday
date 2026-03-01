package whitehall.yesterday.ingestion.parliament

import org.springframework.stereotype.Component
import whitehall.yesterday.ingestion.DateWindow
import whitehall.yesterday.ingestion.ItemRow
import whitehall.yesterday.ingestion.SourceIngester

@Component
class ParliamentBillIngester(private val client: ParliamentBillsClient) : SourceIngester {
    override val sourceName = "parliament_bill"
    override fun fetchItems(window: DateWindow): List<ItemRow> = client.fetchIntroducedBills(window)
}
