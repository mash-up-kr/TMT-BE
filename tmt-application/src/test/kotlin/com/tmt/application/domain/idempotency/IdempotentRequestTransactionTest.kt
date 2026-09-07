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
    fun `키를 비즈니스 로직보다 먼저 선점한다 - 밀린 요청이 로직 예외 대신 재현 경로를 타는 근거다`() {
        var recordsWhenLogicRan = -1

        runAndRecord {
            recordsWhenLogicRan = port.insertedRecords.size
            JoinResult("group_1", 1)
        }

        assertEquals(1, recordsWhenLogicRan, "로직이 돌 때 이미 선점 레코드가 있어야 한다 (TMT-351)")
    }

    @Test
    fun `비즈니스 결과를 직렬화해 같은 호출 안에서 본문을 채운다`() {
        val response = runAndRecord { JoinResult("group_1", 1) }

        assertEquals(JoinResult("group_1", 1), response)
        val recorded = port.find(7, ENDPOINT, "join-1")!!
        assertEquals(201, recorded.responseStatus)
        assertEquals(JoinResult("group_1", 1), codec.deserialize(recorded.responseBody, JoinResult::class.java))
    }

    @Test
    fun `비즈니스 로직의 예외는 그대로 나간다 - 선점 롤백은 트랜잭션 소관이라 통합 테스트가 검증한다`() {
        assertThrows<IllegalStateException> { runAndRecord { error("티켓 부족") } }
    }

    @Test
    fun `선점에 밀리면 비즈니스 로직을 아예 돌지 않는다`() {
        port.beforeInsert = { throw IdempotencyRaceLostException(ENDPOINT, "join-1") }
        var logicRuns = 0

        assertThrows<IdempotencyRaceLostException> {
            runAndRecord {
                logicRuns++
                JoinResult("group_1", 1)
            }
        }

        assertEquals(0, logicRuns, "밀린 쪽은 티켓 소비 같은 부수효과를 만들면 안 된다")
    }

    companion object {
        private const val ENDPOINT = "POST /v1/groups/group_1/memberships"
    }
}
