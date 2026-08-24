package com.tmt.application.domain.idempotency

import com.tmt.application.port.output.persistence.IdempotencyPort
import java.time.Instant

/**
 * INSERT가 선점을 판정하는 실제 어댑터를 흉내 낸다.
 * [beforeInsert]에 다른 요청의 커밋을 끼워 넣으면 경합에 밀린 상황을 만들 수 있다.
 */
class FakeIdempotencyPort : IdempotencyPort {
    private val records = mutableMapOf<Triple<Long, String, String>, IdempotencyRecord>()

    var beforeInsert: (() -> Unit)? = null
    var insertedRecords = mutableListOf<IdempotencyRecord>()
    var deletedBefore: Instant? = null
    var deleteResult: Int = 0

    override fun find(
        userId: Long,
        endpoint: String,
        idemKey: String,
    ): IdempotencyRecord? = records[Triple(userId, endpoint, idemKey)]

    override fun insert(record: IdempotencyRecord) {
        beforeInsert?.invoke()
        if (records.putIfAbsent(keyOf(record), record) != null) {
            throw IdempotencyRaceLostException(record.endpoint, record.idemKey)
        }
        insertedRecords += record
    }

    override fun deleteCreatedBefore(threshold: Instant): Int {
        deletedBefore = threshold
        return deleteResult
    }

    fun seed(record: IdempotencyRecord) {
        records[keyOf(record)] = record
    }

    private fun keyOf(record: IdempotencyRecord) = Triple(record.userId, record.endpoint, record.idemKey)
}
