package whitehall.yesterday.query

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.time.OffsetDateTime

data class ItemResponse(
    val id: String,
    val source: String,
    val title: String,
    val url: String,
    val publishedAt: Instant,
    val updatedAt: Instant?,
    val tags: Map<String, Any?>,
    val createdAt: Instant,
    val modifiedAt: Instant
)

@Repository
class ItemQueryRepository(
    private val jdbc: JdbcTemplate,
    private val objectMapper: ObjectMapper
) {
    fun findById(id: String): ItemResponse? {
        return jdbc.query(
            "SELECT id, source, title, url, published_at, updated_at, tags, created_at, modified_at FROM items WHERE id = ?",
            { rs, _ ->
                val tagsJson = rs.getString("tags")
                val tags: Map<String, Any?> = objectMapper.readValue(tagsJson, object : TypeReference<Map<String, Any?>>() {})
                ItemResponse(
                    id          = rs.getString("id"),
                    source      = rs.getString("source"),
                    title       = rs.getString("title"),
                    url         = rs.getString("url"),
                    publishedAt = rs.getObject("published_at", OffsetDateTime::class.java).toInstant(),
                    updatedAt   = rs.getObject("updated_at", OffsetDateTime::class.java)?.toInstant(),
                    tags        = tags,
                    createdAt   = rs.getObject("created_at", OffsetDateTime::class.java).toInstant(),
                    modifiedAt  = rs.getObject("modified_at", OffsetDateTime::class.java).toInstant()
                )
            },
            id
        ).firstOrNull()
    }
}

@RestController
@RequestMapping("/v1/items")
class ItemController(private val repo: ItemQueryRepository) {

    @GetMapping("/{id}")
    fun getItem(@PathVariable id: String): ResponseEntity<ItemResponse> {
        val item = repo.findById(id) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(item)
    }
}
