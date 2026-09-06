package com.tmt.output.oauth.kakao

import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

/** 테스트에서 실제 카카오를 부르지 않는다 — 나간 파라미터를 그대로 기록해 검증한다. */
class FakeKakaoHttpClient : KakaoHttpClient {
    private val mapper = JsonMapper.builder().build()

    val postCalls = mutableListOf<Pair<String, Map<String, String>>>()
    val getCalls = mutableListOf<Pair<String, String>>()

    var tokenResponse: String = """{"access_token":"access-token","token_type":"bearer"}"""
    var tokenError: Throwable? = null
    var userResponse: String =
        """{"id":12345,"kakao_account":{"profile":{"nickname":"준형이","profile_image_url":"https://img"}}}"""
    var userError: Throwable? = null

    fun statusError(
        status: Int,
        body: String,
    ): KakaoHttpStatusException = KakaoHttpStatusException(status, mapper.readTree(body))

    override fun postForm(
        url: String,
        form: Map<String, String>,
    ): JsonNode {
        postCalls += url to form
        tokenError?.let { throw it }
        return mapper.readTree(tokenResponse)
    }

    override fun getWithBearer(
        url: String,
        accessToken: String,
    ): JsonNode {
        getCalls += url to accessToken
        userError?.let { throw it }
        return mapper.readTree(userResponse)
    }
}
