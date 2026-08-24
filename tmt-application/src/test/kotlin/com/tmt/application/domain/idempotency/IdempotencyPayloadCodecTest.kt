package com.tmt.application.domain.idempotency

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class IdempotencyPayloadCodecTest {
    data class Body(
        val placeId: String,
        val tagIds: List<String> = emptyList(),
        val rating: Int? = null,
    )

    data class Response(
        val saveId: String,
        val createdAt: Instant,
    )

    private val codec = IdempotencyPayloadCodec()

    @Test
    fun `지문은 64자 hex다 - request_fingerprint VARCHAR(64)에 맞는다`() {
        val fingerprint = codec.fingerprint(Body("place_1"))

        assertEquals(IdempotencyRecord.FINGERPRINT_LENGTH, fingerprint.length)
        assertTrue(fingerprint.all { it in "0123456789abcdef" }, fingerprint)
    }

    @Test
    fun `같은 바디면 같은 지문이 나온다`() {
        assertEquals(
            codec.fingerprint(Body("place_1", listOf("tag_a", "tag_b"), 5)),
            codec.fingerprint(Body("place_1", listOf("tag_a", "tag_b"), 5)),
        )
    }

    @Test
    fun `한 필드만 달라도 지문이 갈린다`() {
        assertNotEquals(
            codec.fingerprint(Body("place_1", rating = 4)),
            codec.fingerprint(Body("place_1", rating = 5)),
        )
    }

    @Test
    fun `바디 없는 요청도 지문이 계산된다`() {
        assertEquals(IdempotencyRecord.FINGERPRINT_LENGTH, codec.fingerprint(null).length)
        assertNotEquals(codec.fingerprint(null), codec.fingerprint(Body("place_1")))
    }

    @Test
    fun `응답은 시각 필드까지 그대로 왕복한다`() {
        val response = Response("save_1", Instant.parse("2026-08-23T01:02:03Z"))

        val restored = codec.deserialize(codec.serialize(response), Response::class.java)

        assertEquals(response, restored)
    }
}
