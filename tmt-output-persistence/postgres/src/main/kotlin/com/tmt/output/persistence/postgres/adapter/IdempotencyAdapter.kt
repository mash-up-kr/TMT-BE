package com.tmt.output.persistence.postgres.adapter

import com.tmt.application.domain.idempotency.IdempotencyRaceLostException
import com.tmt.application.domain.idempotency.IdempotencyRecord
import com.tmt.application.port.output.persistence.IdempotencyPort
import com.tmt.output.persistence.postgres.entity.IdempotencyKeyEntity
import com.tmt.output.persistence.postgres.entity.IdempotencyKeyId
import com.tmt.output.persistence.postgres.repository.IdempotencyKeyRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Component
class IdempotencyAdapter(
    private val idempotencyKeyRepository: IdempotencyKeyRepository,
) : IdempotencyPort {
    @Transactional(readOnly = true)
    override fun find(
        userId: Long,
        endpoint: String,
        idemKey: String,
    ): IdempotencyRecord? =
        idempotencyKeyRepository
            .findById(IdempotencyKeyId(userId, endpoint, idemKey))
            .orElse(null)
            ?.toRecord()

    /** 호출부(비즈니스)의 트랜잭션에 반드시 참여한다 — 여기서 새 트랜잭션을 열면 커밋 시점이 갈린다. */
    @Transactional
    override fun insert(record: IdempotencyRecord) {
        val inserted =
            idempotencyKeyRepository.insertIfAbsent(
                userId = record.userId,
                endpoint = record.endpoint,
                idemKey = record.idemKey,
                fingerprint = record.requestFingerprint,
                responseStatus = record.responseStatus,
                responseBody = record.responseBody,
            )
        if (inserted == 0) {
            throw IdempotencyRaceLostException(record.endpoint, record.idemKey)
        }
    }

    @Transactional
    override fun deleteCreatedBefore(threshold: Instant): Int = idempotencyKeyRepository.deleteCreatedBefore(threshold)

    private fun IdempotencyKeyEntity.toRecord(): IdempotencyRecord =
        IdempotencyRecord(
            userId = id.userId,
            endpoint = id.endpoint,
            idemKey = id.idemKey,
            requestFingerprint = requestFingerprint,
            responseStatus = responseStatus.toInt(),
            responseBody = responseBody,
        )
}
