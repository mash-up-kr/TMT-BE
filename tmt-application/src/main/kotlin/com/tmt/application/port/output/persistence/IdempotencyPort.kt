package com.tmt.application.port.output.persistence

import com.tmt.application.domain.idempotency.IdempotencyRaceLostException
import com.tmt.application.domain.idempotency.IdempotencyRecord
import java.time.Instant

interface IdempotencyPort {
    fun find(
        userId: Long,
        endpoint: String,
        idemKey: String,
    ): IdempotencyRecord?

    /**
     * 조회로 선점 여부를 판단하면 동시 요청이 둘 다 통과한다. INSERT를 먼저 시도하고,
     * 같은 키가 이미 있으면 [IdempotencyRaceLostException]을 던진다.
     */
    fun insert(record: IdempotencyRecord)

    /**
     * 선점해 둔 레코드의 응답 본문을 채운다 (TMT-351) — 선점과 같은 트랜잭션에서만 부른다.
     * 대상 행이 없으면 선점 없이 불린 것이라 호출부 결함이다.
     */
    fun updateResponseBody(
        userId: Long,
        endpoint: String,
        idemKey: String,
        responseBody: String,
    )

    /** TTL 정리. 지운 건수를 돌려준다. */
    fun deleteCreatedBefore(threshold: Instant): Int
}
