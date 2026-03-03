package whitehall.yesterday.search

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient

@Service
class EmbeddingService(
    @Qualifier("voyage") private val restClient: RestClient,
    @Value("\${voyage.api-key:}") private val apiKey: String,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(EmbeddingService::class.java)

    /**
     * Embeds a list of texts using Voyage voyage-3-lite (512 dimensions).
     * Batches at 128 inputs per request (Voyage limit).
     * Returns an empty list on error — callers degrade gracefully.
     */
    fun embed(texts: List<String>): List<FloatArray> {
        if (texts.isEmpty()) return emptyList()
        if (apiKey.isBlank()) {
            log.warn("VOYAGE_API_KEY not set — skipping embedding generation")
            return emptyList()
        }
        return try {
            texts.chunked(BATCH_SIZE).flatMap { batch ->
                val requestBody = objectMapper.writeValueAsString(
                    mapOf("input" to batch, "model" to "voyage-3-lite")
                )
                val response = restClient.post()
                    .uri("/v1/embeddings")
                    .header("Authorization", "Bearer $apiKey")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(JsonNode::class.java)
                    ?: error("null response from Voyage API")

                response.get("data")
                    .sortedBy { it.get("index").asInt() }
                    .map { item ->
                        val arr = item.get("embedding")
                        FloatArray(arr.size()) { i -> arr.get(i).floatValue() }
                    }
            }
        } catch (e: Exception) {
            log.error("Embedding generation failed: ${e.message}")
            emptyList()
        }
    }

    companion object {
        private const val BATCH_SIZE = 128
    }
}
