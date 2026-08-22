package com.tmt.output.persistence.postgres.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

enum class MediaAssetStatus {
    STAGED,
    ATTACHED,
}

@Entity
@Table(name = "media_asset")
class MediaAssetEntity(
    @Column(nullable = false)
    val ownerId: Long,
    @Column(length = 300, nullable = false, unique = true)
    val s3Key: String,
    @Column(length = 50, nullable = false)
    val contentType: String,
    @Column(nullable = false)
    val contentLength: Long,
) : BaseCreatedEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    /** STAGED → ATTACHED 단방향. 첨부는 조건부 UPDATE로 한다 — 읽고 쓰면 같은 파일이 두 번 붙는다. */
    @Enumerated(EnumType.STRING)
    @Column(length = 10, nullable = false)
    var status: MediaAssetStatus = MediaAssetStatus.STAGED
        protected set

    var attachedAt: Instant? = null
        protected set
}
