package whitehall.yesterday.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.net.http.HttpClient
import java.time.Duration

@Configuration
class WebClientConfig {

    @Bean
    fun govukRestClient(builder: RestClient.Builder): RestClient {
        val httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build()

        val factory = JdkClientHttpRequestFactory(httpClient)
        factory.setReadTimeout(Duration.ofSeconds(30))

        return builder
            .requestFactory(factory)
            .baseUrl("https://www.gov.uk")
            .defaultHeader("User-Agent", "whitehall-yesterday/1.0")
            .build()
    }
}
