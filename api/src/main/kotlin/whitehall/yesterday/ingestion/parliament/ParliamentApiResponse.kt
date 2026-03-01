package whitehall.yesterday.ingestion.parliament

// ── Bills API ────────────────────────────────────────────────────────────────

data class BillSittingsResponse(
    val items: List<BillSitting> = emptyList(),
    val totalResults: Int = 0,
    val itemsPerPage: Int = 0
)

data class BillSitting(
    val id: Int = 0,
    val stageId: Int = 0,
    val billStageId: Int = 0,
    val billId: Int = 0,
    val date: String? = null
)

data class BillSummary(
    val billId: Int = 0,
    val shortTitle: String? = null,
    val currentHouse: String? = null,
    val lastUpdate: String? = null
)

// ── Statutory Instruments API v2 ─────────────────────────────────────────────

data class SiListResponse(
    val items: List<SiListItem> = emptyList(),
    val totalResults: Int = 0,
    val itemsPerPage: Int = 0
)

data class SiListItem(
    val value: SiSummary? = null
)

data class SiSummary(
    val id: String = "",
    val name: String? = null,
    val paperPrefix: String? = null,
    val paperNumber: Int? = null,
    val paperYear: String? = null,
    val commonsLayingDate: String? = null,
    val lordsLayingDate: String? = null,
    val workpackageId: String? = null,
    val procedure: SiProcedure? = null
)

data class SiProcedure(
    val name: String? = null
)
