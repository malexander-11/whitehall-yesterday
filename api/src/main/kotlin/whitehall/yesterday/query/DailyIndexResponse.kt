package whitehall.yesterday.query

import java.time.Instant
import java.time.LocalDate

data class DailyIndexResponse(
    val date: LocalDate,
    val totalCount: Int,
    val newCount: Int,
    val updatedCount: Int,
    val items: List<IndexItem>
)

data class IndexItem(
    val id: String,
    val bucket: String,
    val title: String,
    val url: String,
    val publishedAt: Instant,
    val updatedAt: Instant?,
    val format: String?,
    val organisations: List<String>
)
