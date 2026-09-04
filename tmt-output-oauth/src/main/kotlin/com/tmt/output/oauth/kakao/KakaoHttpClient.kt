package com.tmt.output.oauth.kakao

import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import tools.jackson.databind.JsonNode
import java.time.Duration

/**
 * 카카오 호출의 최소 경계. 어댑터가 이 인터페이스에만 의존해 테스트에서 실제 카카오를 부르지 않는다.
 */
interface KakaoHttpClient {
    /** 4xx·5xx는 [KakaoHttpStatusException]으로, 네트워크 실패는 그 외 예외로 나간다 */
    fun postForm(
        url: String,
        form: Map<String, String>,
    ): JsonNode

    fun getWithBearer(
        url: String,
        accessToken: String,
    ): JsonNode
}

/** 카카오가 오류 상태를 응답했다. 본문의 error·error_code로 사유를 구분한다 */
class KakaoHttpStatusException(
    val status: Int,
    val body: JsonNode?,
) : RuntimeException("카카오 응답 status=$status")

@Component
class RestClientKakaoHttpClient : KakaoHttpClient {
    private val restClient: RestClient =
        RestClient
            .builder()
            .requestFactory(
                SimpleClientHttpRequestFactory().apply {
                    setConnectTimeout(Duration.ofSeconds(2))
                    setReadTimeout(Duration.ofSeconds(5))
                },
            ).build()

    override fun postForm(
        url: String,
        form: Map<String, String>,
    ): JsonNode {
        val body = LinkedMultiValueMap<String, String>().apply { form.forEach { (k, v) -> add(k, v) } }
        return restClient
            .post()
            .uri(url)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(body)
            .exchange { _, response -> response.readJsonOrThrow() }
    }

    override fun getWithBearer(
        url: String,
        accessToken: String,
    ): JsonNode =
        restClient
            .get()
            .uri(url)
            .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
            .exchange { _, response -> response.readJsonOrThrow() }

    /** 오류 상태에서도 본문을 먼저 읽는다 — 카카오는 실패 사유를 본문에 싣는다 */
    private fun RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse.readJsonOrThrow(): JsonNode {
        val json = runCatching { bodyTo(JsonNode::class.java) }.getOrNull()
        if (statusCode.isError) throw KakaoHttpStatusException(statusCode.value(), json)
        return json ?: error("카카오 응답 본문이 비어 있다")
    }
}
