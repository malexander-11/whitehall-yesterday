package whitehall.yesterday.search

data class SearchRequest(
    val query: String,
    val limit: Int = 50,
    val offset: Int = 0
)
