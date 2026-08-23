package com.tmt.application.domain.idempotency

import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule
import java.security.MessageDigest

/**
 * 멱등 레코드에 넣을 JSON과 요청 지문을 만든다.
 *
 * 애플리케이션 공용 ObjectMapper를 주입받지 않고 전용 매퍼를 쓴다 — 전역 직렬화 설정을
 * 손대면 이미 기록된 지문이 전부 어긋나 같은 요청이 IDEMPOTENCY_CONFLICT가 된다.
 */
@Component
class IdempotencyPayloadCodec {
    private val mapper: ObjectMapper =
        JsonMapper
            .builder()
            .addModule(KotlinModule.Builder().build())
            .build()

    fun serialize(value: Any): String = mapper.writeValueAsString(value)

    fun <T : Any> deserialize(
        json: String,
        type: Class<T>,
    ): T = mapper.readValue(json, type)

    /**
     * 요청 바디의 SHA-256 hex(64자). 프로퍼티 순서는 선언 순서로 고정이라 같은 바디면 같은 값이 나온다.
     */
    fun fingerprint(payload: Any?): String {
        val json = payload?.let(::serialize) ?: NO_PAYLOAD
        return MessageDigest
            .getInstance("SHA-256")
            .digest(json.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    companion object {
        /** 바디 없는 요청(그룹 가입 등)도 지문이 있어야 PK 재사용을 잡아낼 수 있다. */
        private const val NO_PAYLOAD = "null"
    }
}
