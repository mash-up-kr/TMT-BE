package com.tmt.application.domain.idempotency

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class IdempotencyRecordTest {
    private fun record(
        endpoint: String = "POST /v1/saves",
        idemKey: String = "key-1",
        fingerprint: String = "a".repeat(IdempotencyRecord.FINGERPRINT_LENGTH),
    ) = IdempotencyRecord(
        userId = 1,
        endpoint = endpoint,
        idemKey = idemKey,
        requestFingerprint = fingerprint,
        responseStatus = 201,
        responseBody = "{}",
    )

    @Test
    fun `컬럼 길이를 넘는 값은 DB가 아니라 여기서 막는다`() {
        assertThrows<IllegalArgumentException> {
            record(
                endpoint = "e".repeat(IdempotencyRecord.ENDPOINT_MAX_LENGTH + 1),
            )
        }
        assertThrows<IllegalArgumentException> {
            record(
                idemKey = "k".repeat(IdempotencyRecord.IDEM_KEY_MAX_LENGTH + 1),
            )
        }
        assertThrows<IllegalArgumentException> { record(fingerprint = "abc") }
    }

    @Test
    fun `한계 길이는 그대로 통과한다`() {
        val endpoint = "e".repeat(IdempotencyRecord.ENDPOINT_MAX_LENGTH)
        assertEquals(endpoint, record(endpoint = endpoint).endpoint)
    }
}
