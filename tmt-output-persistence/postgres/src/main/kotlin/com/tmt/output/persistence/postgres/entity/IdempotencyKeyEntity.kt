package com.tmt.output.persistence.postgres.entity

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.io.Serializable

@Embeddable
data class IdempotencyKeyId(
    @Column(nullable = false)
    val userId: Long,
    @Column(length = 80, nullable = false)
    val endpoint: String,
    @Column(length = 100, nullable = false)
    val idemKey: String,
) : Serializable

@Entity
@Table(name = "idempotency_key")
class IdempotencyKeyEntity(
    @EmbeddedId
    val id: IdempotencyKeyId,
    /** 바디 해시. 같은 키인데 이 값이 다르면 IDEMPOTENCY_CONFLICT다. */
    @Column(length = 64, nullable = false)
    val requestFingerprint: String,
    @Column(nullable = false)
    val responseStatus: Short,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    val responseBody: String,
) : BaseCreatedEntity()
