package com.tmt.application.domain.idempotency

import com.tmt.application.port.input.IdempotentRequest
import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class IdempotencyServiceTest {
    data class SaveResult(
        val saveId: String,
        val grantedCount: Int,
    )

    data class SaveBody(
        val placeId: String,
        val rating: Int? = null,
    )

    private val port = FakeIdempotencyPort()
    private val codec = IdempotencyPayloadCodec()
    private val service = IdempotencyService(port, codec, IdempotentRequestTransaction(port, codec))

    private var runCount = 0

    private fun request(
        idemKey: String = "key-1",
        endpoint: String = ENDPOINT,
        payload: Any? = SaveBody("place_1"),
        userId: Long = 1,
    ) = IdempotentRequest(
        userId = userId,
        endpoint = endpoint,
        idemKey = idemKey,
        payload = payload,
        responseType = SaveResult::class.java,
        successStatus = 201,
    )

    private fun run(
        idemKey: String = "key-1",
        endpoint: String = ENDPOINT,
        payload: Any? = SaveBody("place_1"),
        userId: Long = 1,
        result: SaveResult = SaveResult("save_1", 1),
    ) = service.execute(request(idemKey, endpoint, payload, userId)) {
        runCount++
        result
    }

    @Test
    fun `처음 보는 키면 비즈니스 로직을 실행하고 최초 응답을 기록한다`() {
        val outcome = run()

        assertEquals(SaveResult("save_1", 1), outcome.response)
        assertEquals(201, outcome.status)
        assertFalse(outcome.replayed)
        assertEquals(1, runCount)
        assertEquals(1, port.insertedRecords.size)
        assertEquals(ENDPOINT, port.insertedRecords.single().endpoint)
    }

    @Test
    fun `같은 키에 같은 바디면 비즈니스 로직을 다시 타지 않고 최초 응답을 그대로 돌려준다`() {
        run(result = SaveResult("save_1", 1))

        val retried = run(result = SaveResult("save_2", 0))

        assertEquals(SaveResult("save_1", 1), retried.response)
        assertEquals(201, retried.status)
        assertTrue(retried.replayed)
        assertEquals(1, runCount)
    }

    @Test
    fun `같은 키에 다른 바디면 IDEMPOTENCY_CONFLICT다`() {
        run(payload = SaveBody("place_1"))

        val e = assertThrows<TmtException> { run(payload = SaveBody("place_1", rating = 5)) }

        assertEquals(ErrorCode.IDEMPOTENCY_CONFLICT, e.errorCode)
        assertEquals(1, runCount)
    }

    @Test
    fun `endpoint가 다르면 같은 키라도 서로의 응답을 재현하지 않는다`() {
        run(endpoint = "POST /v1/saves", result = SaveResult("save_1", 1))

        val other = run(endpoint = "PUT /v1/saves/save_9", result = SaveResult("save_9", 0))

        assertEquals(SaveResult("save_9", 0), other.response)
        assertFalse(other.replayed)
        assertEquals(2, runCount)
    }

    @Test
    fun `사용자가 다르면 같은 키라도 서로의 응답을 재현하지 않는다`() {
        run(userId = 1, result = SaveResult("save_1", 1))

        val other = run(userId = 2, result = SaveResult("save_2", 1))

        assertEquals(SaveResult("save_2", 1), other.response)
        assertFalse(other.replayed)
    }

    @Test
    fun `경합에 밀리면 내 비즈니스 결과를 버리고 먼저 커밋된 응답을 재현한다`() {
        // INSERT 직전에 다른 요청이 같은 키로 커밋한 상황
        port.beforeInsert = {
            port.seed(
                IdempotencyRecord(
                    userId = 1,
                    endpoint = ENDPOINT,
                    idemKey = "key-1",
                    requestFingerprint = codec.fingerprint(SaveBody("place_1")),
                    responseStatus = 201,
                    responseBody = codec.serialize(SaveResult("save_winner", 1)),
                ),
            )
            port.beforeInsert = null
        }

        val outcome = run(result = SaveResult("save_loser", 1))

        assertEquals(SaveResult("save_winner", 1), outcome.response)
        assertTrue(outcome.replayed)
        // 비즈니스 로직은 한 번 돌았지만 그 트랜잭션이 롤백돼 결과는 버려진다
        assertEquals(1, runCount)
        assertTrue(port.insertedRecords.isEmpty())
    }

    @Test
    fun `경합에 밀렸는데 기록을 다시 찾지 못하면 IDEMPOTENCY_CONFLICT다`() {
        port.beforeInsert = { throw IdempotencyRaceLostException(ENDPOINT, "key-1") }

        val e = assertThrows<TmtException> { run() }

        assertEquals(ErrorCode.IDEMPOTENCY_CONFLICT, e.errorCode)
    }

    companion object {
        private const val ENDPOINT = "POST /v1/saves"
    }
}
