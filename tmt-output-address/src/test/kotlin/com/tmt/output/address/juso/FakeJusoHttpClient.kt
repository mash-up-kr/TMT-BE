package com.tmt.output.address.juso

import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

/** 테스트에서 실제 juso를 부르지 않는다 — 나간 파라미터를 그대로 기록해 검증한다. */
class FakeJusoHttpClient(
    private var responseJson: String = SUCCESS_EMPTY,
) : JusoHttpClient {
    private val mapper = JsonMapper.builder().build()

    val calls = mutableListOf<Pair<String, Map<String, String>>>()
    var throwOnCall: Boolean = false

    fun respondWith(json: String) {
        responseJson = json
    }

    override fun getJson(
        path: String,
        params: Map<String, String>,
    ): JsonNode {
        calls += path to params
        if (throwOnCall) throw RuntimeException("juso 타임아웃")
        return mapper.readTree(responseJson)
    }

    companion object {
        const val SUCCESS_EMPTY =
            """{"results":{"common":{"errorCode":"0","errorMessage":"정상","totalCount":"0"},"juso":[]}}"""
    }
}
