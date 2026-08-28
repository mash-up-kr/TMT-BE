package com.tmt.application.domain.media

import com.tmt.application.port.output.persistence.MediaAssetPort
import com.tmt.application.port.output.persistence.MediaAssetSnapshot
import java.time.Instant

/**
 * 조건부 전이(TMT-177)를 인메모리로 흉내내는 대역 — `markAttached`는 STAGED인 행만 옮기고
 * 옮긴 수를 돌려준다. 이 수가 요청 수와 다른 것이 경합 판정의 근거다.
 */
class FakeMediaAssetPort : MediaAssetPort {
    private data class Row(
        val id: Long,
        val ownerId: Long,
        val s3Key: String,
        var attached: Boolean,
        val createdAt: Instant,
    )

    private val rows = mutableMapOf<Long, Row>()
    private var nextId = 1L

    val deleted = mutableListOf<Long>()

    fun seed(
        ownerId: Long = 1,
        attached: Boolean = false,
        createdAt: Instant = Instant.parse("2026-08-27T00:00:00Z"),
    ): Long {
        val id = nextId++
        rows[id] = Row(id, ownerId, "review/$id.jpg", attached, createdAt)
        return id
    }

    fun isAttached(id: Long): Boolean = rows.getValue(id).attached

    fun exists(id: Long): Boolean = id in rows

    override fun createStaged(
        ownerId: Long,
        s3Key: String,
        contentType: String,
        contentLength: Long,
    ): Long {
        val id = nextId++
        rows[id] = Row(id, ownerId, s3Key, attached = false, createdAt = Instant.parse("2026-08-27T00:00:00Z"))
        return id
    }

    override fun findByIds(assetIds: Collection<Long>): List<MediaAssetSnapshot> =
        assetIds.mapNotNull { rows[it] }.map { MediaAssetSnapshot(it.id, it.ownerId, it.s3Key, it.attached) }

    override fun markAttached(assetIds: Collection<Long>): Int =
        assetIds.count { id ->
            val row = rows[id]
            if (row != null && !row.attached) {
                row.attached = true
                true
            } else {
                false
            }
        }

    override fun markStaged(assetIds: Collection<Long>): Int =
        assetIds.count { id ->
            val row = rows[id]
            if (row != null && row.attached) {
                row.attached = false
                true
            } else {
                false
            }
        }

    override fun findStagedCreatedBefore(threshold: Instant): List<MediaAssetSnapshot> =
        rows.values
            .filter { !it.attached && it.createdAt.isBefore(threshold) }
            .map { MediaAssetSnapshot(it.id, it.ownerId, it.s3Key, it.attached) }

    override fun deleteByIds(assetIds: Collection<Long>): Int {
        deleted += assetIds
        return assetIds.count { rows.remove(it) != null }
    }
}
