package com.tmt.application.port.output.persistence

import java.time.Instant

interface MediaAssetPort {
    /** STAGED로 생성하고 id를 돌려준다. */
    fun createStaged(
        ownerId: Long,
        s3Key: String,
        contentType: String,
        contentLength: Long,
    ): Long

    fun findByIds(assetIds: Collection<Long>): List<MediaAssetSnapshot>

    /**
     * STAGED → ATTACHED 조건부 전이. 전이된 행 수를 돌려준다 — 요청 수와 다르면
     * 검증과 부착 사이에 경합이 있었다는 뜻이다.
     */
    fun markAttached(assetIds: Collection<Long>): Int

    /** ATTACHED → STAGED 되돌림 (이어쓰기 교체로 빠진 사진). 되돌린 행 수를 돌려준다. */
    fun markStaged(assetIds: Collection<Long>): Int

    /** [threshold] 이전에 생성된 STAGED 자산 (M4 TTL 정리 대상). */
    fun findStagedCreatedBefore(threshold: Instant): List<MediaAssetSnapshot>

    fun deleteByIds(assetIds: Collection<Long>): Int
}

data class MediaAssetSnapshot(
    val id: Long,
    val ownerId: Long,
    val s3Key: String,
    val attached: Boolean,
)
