package whitehall.yesterday.search

import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import whitehall.yesterday.query.DailyIndexResponse
import java.time.LocalDate

@RestController
@RequestMapping("/v1/days")
class SearchController(
    private val embeddingService: EmbeddingService,
    private val searchRepository: SearchRepository
) {

    @PostMapping("/{date}/search")
    fun search(
        @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate,
        @RequestBody request: SearchRequest
    ): ResponseEntity<DailyIndexResponse> {
        if (request.query.length < 3) {
            return ResponseEntity.badRequest().build()
        }

        // Expand query for lexical FTS (acronyms, synonyms); embed original query only
        val lexicalQueries = QueryExpander.expand(request.query)
        val queryEmbedding = embeddingService.embed(listOf(request.query)).firstOrNull()

        val items = searchRepository.search(
            date           = date,
            displayQuery   = request.query,
            lexicalQueries = lexicalQueries,
            queryEmbedding = queryEmbedding,
            limit          = request.limit.coerceIn(1, 200),
            offset         = request.offset.coerceAtLeast(0)
        )

        return ResponseEntity.ok(
            DailyIndexResponse(
                date         = date,
                totalCount   = items.size,
                newCount     = items.count { it.bucket == "NEW" },
                updatedCount = items.count { it.bucket == "UPDATED" },
                items        = items,
                query        = request.query
            )
        )
    }
}
