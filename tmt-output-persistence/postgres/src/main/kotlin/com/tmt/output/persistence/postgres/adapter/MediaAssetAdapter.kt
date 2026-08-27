package com.tmt.output.persistence.postgres.adapter

import com.tmt.application.port.output.persistence.MediaAssetPort
import com.tmt.application.port.output.persistence.MediaAssetSnapshot
import com.tmt.output.persistence.postgres.entity.MediaAssetEntity
import com.tmt.output.persistence.postgres.entity.MediaAssetStatus
import com.tmt.output.persistence.postgres.repository.MediaAssetRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Component
class MediaAssetAdapter(
    private val mediaAssetRepository: MediaAssetRepository,
) : MediaAssetPort {
    @Transactional
    override fun createStaged(
        ownerId: Long,
        s3Key: String,
        contentType: String,
        contentLength: Long,
    ): Long =
        mediaAssetRepository
            .save(
                MediaAssetEntity(
                    ownerId = ownerId,
                    s3Key = s3Key,
                    contentType = contentType,
                    contentLength = contentLength,
                ),
            ).id

    @Transactional(readOnly = true)
    override fun findByIds(assetIds: Collection<Long>): List<MediaAssetSnapshot> =
        mediaAssetRepository.findAllById(assetIds).map { it.toSnapshot() }

    @Transactional
    override fun markAttached(assetIds: Collection<Long>): Int =
        mediaAssetRepository.markAttached(assetIds, Instant.now())

    @Transactional
    override fun markStaged(assetIds: Collection<Long>): Int = mediaAssetRepository.markStaged(assetIds)

    @Transactional(readOnly = true)
    override fun findStagedCreatedBefore(threshold: Instant): List<MediaAssetSnapshot> =
        mediaAssetRepository
            .findAllByStatusAndCreatedAtBefore(MediaAssetStatus.STAGED, threshold)
            .map { it.toSnapshot() }

    @Transactional
    override fun deleteByIds(assetIds: Collection<Long>): Int = mediaAssetRepository.deleteAllByIdIn(assetIds)

    private fun MediaAssetEntity.toSnapshot(): MediaAssetSnapshot =
        MediaAssetSnapshot(
            id = id,
            ownerId = ownerId,
            s3Key = s3Key,
            attached = status == MediaAssetStatus.ATTACHED,
        )
}
