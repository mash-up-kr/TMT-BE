package com.tmt.output.persistence.postgres.repository

import com.tmt.output.persistence.postgres.entity.IdempotencyKeyEntity
import com.tmt.output.persistence.postgres.entity.IdempotencyKeyId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface IdempotencyKeyRepository : JpaRepository<IdempotencyKeyEntity, IdempotencyKeyId> {
    /**
     * `save()`는 할당된 식별자 때문에 merge로 돌아 기존 행을 덮어쓴다. 선점 여부를 DB가 판정하도록
     * INSERT ... ON CONFLICT DO NOTHING을 쓰고, 삽입 건수(0 또는 1)로 승패를 가른다.
     * created_at은 컬럼 기본값(now())에 맡긴다.
     */
    @Modifying(flushAutomatically = true)
    @Query(
        value = """
            INSERT INTO idempotency_key
                (user_id, endpoint, idem_key, request_fingerprint, response_status, response_body)
            VALUES
                (:userId, :endpoint, :idemKey, :fingerprint, :responseStatus, CAST(:responseBody AS jsonb))
            ON CONFLICT (user_id, endpoint, idem_key) DO NOTHING
        """,
        nativeQuery = true,
    )
    fun insertIfAbsent(
        @Param("userId") userId: Long,
        @Param("endpoint") endpoint: String,
        @Param("idemKey") idemKey: String,
        @Param("fingerprint") fingerprint: String,
        @Param("responseStatus") responseStatus: Int,
        @Param("responseBody") responseBody: String,
    ): Int

    @Modifying
    @Query("DELETE FROM IdempotencyKeyEntity e WHERE e.createdAt < :threshold")
    fun deleteCreatedBefore(
        @Param("threshold") threshold: Instant,
    ): Int
}
