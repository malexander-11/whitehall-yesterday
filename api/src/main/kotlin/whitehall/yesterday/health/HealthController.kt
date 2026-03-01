package whitehall.yesterday.health

import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class HealthController(private val jdbc: JdbcTemplate) {

    // Always returns 200 if the process is alive.
    @GetMapping("/health")
    fun health(): ResponseEntity<Map<String, String>> =
        ResponseEntity.ok(mapOf("status" to "UP"))

    // Returns 200 only when Postgres is reachable AND Flyway migrations have run.
    // Returns 503 otherwise so load-balancers / Docker healthchecks can react.
    @GetMapping("/ready")
    fun ready(): ResponseEntity<Map<String, String>> {
        return try {
            jdbc.queryForObject("SELECT 1", Int::class.java)

            val migrationCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = true",
                Int::class.java
            ) ?: 0

            if (migrationCount > 0) {
                ResponseEntity.ok(mapOf("status" to "READY"))
            } else {
                ResponseEntity.status(503).body(mapOf("status" to "MIGRATIONS_PENDING"))
            }
        } catch (e: Exception) {
            ResponseEntity.status(503).body(
                mapOf("status" to "DB_UNAVAILABLE", "error" to (e.message ?: "unknown"))
            )
        }
    }
}
