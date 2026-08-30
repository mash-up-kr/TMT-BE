package com.tmt.output.address.juso

import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.body
import org.springframework.web.util.UriComponentsBuilder
import tools.jackson.databind.JsonNode
import java.time.Duration

/**
 * juso 호출의 최소 경계. 어댑터가 이 인터페이스에만 의존해 테스트에서 실제 juso를 부르지 않는다.
 */
interface JusoHttpClient {
    /** 실패는 예외로 — 어댑터가 차단기에 기록하고 502로 바꾼다 */
    fun getJson(
        path: String,
        params: Map<String, String>,
    ): JsonNode
}

@Component
class RestClientJusoHttpClient(
    baseUrl: String = BASE_URL,
) : JusoHttpClient {
    private val restClient: RestClient =
        RestClient
            .builder()
            .baseUrl(baseUrl)
            .requestFactory(
                org.springframework.http.client.SimpleClientHttpRequestFactory().apply {
                    setConnectTimeout(Duration.ofSeconds(2))
                    setReadTimeout(Duration.ofSeconds(3))
                },
            ).build()

    override fun getJson(
        path: String,
        params: Map<String, String>,
    ): JsonNode {
        val uri =
            UriComponentsBuilder
                .fromPath(path)
                .apply { params.forEach { (k, v) -> queryParam(k, v) } }
                .build()
                .toUriString()
        return restClient
            .get()
            .uri(uri)
            .retrieve()
            .body<JsonNode>()
            ?: error("juso 응답 본문이 비어 있다 - path=$path")
    }

    companion object {
        const val BASE_URL = "https://business.juso.go.kr/addrlink"
    }
}
