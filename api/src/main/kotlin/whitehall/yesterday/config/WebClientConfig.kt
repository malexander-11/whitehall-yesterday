package whitehall.yesterday.config

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.net.http.HttpClient
import java.time.Duration

@Configuration
class WebClientConfig {

    private fun buildFactory(connectSecs: Long = 10, readSecs: Long = 30): JdkClientHttpRequestFactory {
        val httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(connectSecs))
            .build()
        return JdkClientHttpRequestFactory(httpClient).also {
            it.setReadTimeout(Duration.ofSeconds(readSecs))
        }
    }

    @Bean
    fun govukRestClient(builder: RestClient.Builder): RestClient = builder
        .requestFactory(buildFactory())
        .baseUrl("https://www.gov.uk")
        .defaultHeader("User-Agent", "whitehall-yesterday/1.0")
        .build()

    @Bean
    @Qualifier("parliamentBills")
    fun parliamentBillsRestClient(builder: RestClient.Builder): RestClient = builder
        .requestFactory(buildFactory())
        .baseUrl("https://bills-api.parliament.uk")
        .defaultHeader("User-Agent", "whitehall-yesterday/1.0")
        .build()

    @Bean
    @Qualifier("parliamentSIs")
    fun parliamentSIsRestClient(builder: RestClient.Builder): RestClient = builder
        .requestFactory(buildFactory())
        .baseUrl("https://statutoryinstruments-api.parliament.uk")
        .defaultHeader("User-Agent", "whitehall-yesterday/1.0")
        .build()
}
