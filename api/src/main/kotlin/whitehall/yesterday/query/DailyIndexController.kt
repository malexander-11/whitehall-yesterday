package whitehall.yesterday.query

import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
@RequestMapping("/v1/days")
class DailyIndexController(private val repo: DailyIndexQueryRepository) {

    @GetMapping("/{date}")
    fun getIndex(
        @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate
    ): ResponseEntity<DailyIndexResponse> {
        val response = repo.findByDate(date) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(response)
    }
}
