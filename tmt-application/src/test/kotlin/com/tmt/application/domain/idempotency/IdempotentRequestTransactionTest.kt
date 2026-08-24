package com.tmt.application.domain.idempotency

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class IdempotentRequestTransactionTest {
    data class JoinResult(
        val groupId: String,
        val consumedCount: Int,
    )

    private val port = FakeIdempotencyPort()
    private val codec = IdempotencyPayloadCodec()
    private val transaction = IdempotentRequestTransaction(port, codec)

    private fun runAndRecord(block: () -> JoinResult) =
        transaction.runAndRecord(
            userId = 7,
            endpoint = ENDPOINT,
            idemKey = "join-1",
            requestFingerprint = codec.fingerprint(null),
            responseStatus = 201,
            businessLogic = block,
        )

    @Test
    fun `비즈니스 결과를 직렬화해 같은 호출 안에서 기록한다`() {
        val response = runAndRecord { JoinResult("group_1", 1) }

        assertEquals(JoinResult("group_1", 1), response)
        val recorded = port.insertedRecords.single()
        assertEquals(7, recorded.userId)
        assertEquals(ENDPOINT, recorded.endpoint)
        assertEquals(201, recorded.responseStatus)
        assertEquals(JoinResult("group_1", 1), codec.deserialize(recorded.responseBody, JoinResult::class.java))
    }

    @Test
    fun `비즈니스 로직이 실패하면 기록도 남지 않는다`() {
        assertThrows<IllegalStateException> { runAndRecord { error("티켓 부족") } }

        assertEquals(0, port.insertedRecords.size)
    }

    @Test
    fun `선점에 밀린 예외는 이 경계를 그대로 뚫고 나간다 - 비즈니스 트랜잭션을 롤백시켜야 한다`() {
        port.beforeInsert = { throw IdempotencyRaceLostException(ENDPOINT, "join-1") }

        assertThrows<IdempotencyRaceLostException> { runAndRecord { JoinResult("group_1", 1) } }
    }

    companion object {
        private const val ENDPOINT = "POST /v1/groups/group_1/memberships"
    }
}
