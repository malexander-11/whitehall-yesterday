package whitehall.yesterday.ingestion.parliament

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import whitehall.yesterday.ingestion.DateWindow
import whitehall.yesterday.ingestion.ItemRow
import java.security.MessageDigest
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Fetches Bills introduced in Parliament on a given date.
 *
 * Strategy:
 *  1. Call /api/v1/Sittings with DateFrom/DateTo for the target date.
 *  2. Filter to stageId=1 (1st reading = bill introduction).
 *  3. Fetch each introduced bill's details from /api/v1/Bills/{billId}.
 *
 * On a typical sitting day there are 0–20 bills introduced; on recess days there
 * are zero.  This is very low volume so no stop-condition pagination is needed.
 */
@Component
class ParliamentBillsClient(
    @Qualifier("parliamentBills") private val client: RestClient
) {
    private val log = LoggerFactory.getLogger(ParliamentBillsClient::class.java)

    fun fetchIntroducedBills(window: DateWindow): List<ItemRow> {
        val date = window.date
        log.info("Parliament Bills: fetching sittings for date={}", date)

        val sittings = fetchSittings(date)
        val introducedBillIds = sittings
            .filter { it.stageId == 1 }   // stageId=1 = 1st reading = introduction
            .map { it.billId }
            .distinct()

        log.info("Parliament Bills: {} bills introduced on date={}", introducedBillIds.size, date)

        return introducedBillIds.mapNotNull { billId ->
            try {
                val bill = fetchBill(billId)
                mapToItemRow(bill, date)
            } catch (e: Exception) {
                log.warn("Parliament Bills: failed to fetch billId={}, skipping: {}", billId, e.message)
                null
            }
        }
    }

    private fun fetchSittings(date: LocalDate): List<BillSitting> {
        val from = "${date}T00:00:00"
        val to   = "${date}T23:59:59"
        val response = client.get()
            .uri("/api/v1/Sittings?DateFrom={from}&DateTo={to}&Take=200", from, to)
            .retrieve()
            .body(BillSittingsResponse::class.java)
            ?: BillSittingsResponse()
        log.debug("Parliament Bills: sittings response totalResults={}", response.totalResults)
        return response.items
    }

    private fun fetchBill(billId: Int): BillSummary =
        client.get()
            .uri("/api/v1/Bills/{billId}", billId)
            .retrieve()
            .body(BillSummary::class.java)
            ?: throw RuntimeException("Empty response for billId=$billId")

    private fun mapToItemRow(bill: BillSummary, date: LocalDate): ItemRow = ItemRow(
        id              = sha256("parliament:bill:${bill.billId}"),
        source          = "parliament",
        title           = bill.shortTitle?.ifBlank { null } ?: "Untitled Bill",
        url             = "https://bills.parliament.uk/bills/${bill.billId}",
        publishedAt     = date.atStartOfDay(ZoneOffset.UTC).toInstant(),
        updatedAt       = null,
        tags            = buildMap {
            bill.currentHouse?.let { put("house", it) }
        },
        raw             = bill,
        sourceSubtype   = "bill",
        sourceReference = "bill/${bill.billId}"
    )

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
