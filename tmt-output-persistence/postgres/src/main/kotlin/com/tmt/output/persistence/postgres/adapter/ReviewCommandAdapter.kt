package com.tmt.output.persistence.postgres.adapter

import com.tmt.application.port.output.persistence.ReviewCommandPort
import com.tmt.output.persistence.postgres.repository.ReviewDetailRepository
import com.tmt.output.persistence.postgres.repository.SavePhotoRepository
import com.tmt.output.persistence.postgres.repository.SaveRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Component
class ReviewCommandAdapter(
    private val reviewDetailRepository: ReviewDetailRepository,
    private val saveRepository: SaveRepository,
    private val savePhotoRepository: SavePhotoRepository,
) : ReviewCommandPort {
    @Transactional
    override fun deletePhotoLinks(saveId: Long): List<Long> {
        val assetIds = savePhotoRepository.findMediaAssetIds(saveId)
        if (assetIds.isNotEmpty()) savePhotoRepository.deleteBySaveId(saveId)
        return assetIds
    }

    @Transactional
    override fun softDeleteReview(reviewId: Long): Int = reviewDetailRepository.softDelete(reviewId, Instant.now())

    @Transactional
    override fun softDeleteSave(saveId: Long): Int = saveRepository.softDelete(saveId, Instant.now())
}
