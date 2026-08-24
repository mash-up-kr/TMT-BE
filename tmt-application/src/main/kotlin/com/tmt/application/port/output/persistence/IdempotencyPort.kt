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

    /** TTL 정리. 지운 건수를 돌려준다. */
    fun deleteCreatedBefore(threshold: Instant): Int
}
