package com.tmt.output.persistence.postgres.adapter

import com.tmt.application.port.output.persistence.SaveCommandPort
import com.tmt.output.persistence.postgres.entity.ReviewEntity
import com.tmt.output.persistence.postgres.entity.SaveEntity
import com.tmt.output.persistence.postgres.entity.SavePhotoEntity
import com.tmt.output.persistence.postgres.entity.SaveTagEntity
import com.tmt.output.persistence.postgres.entity.SaveTagId
import com.tmt.output.persistence.postgres.repository.ReviewRepository
import com.tmt.output.persistence.postgres.repository.SavePhotoRepository
import com.tmt.output.persistence.postgres.repository.SaveRepository
import com.tmt.output.persistence.postgres.repository.SaveTagRepository
import org.springframework.stereotype.Component

@Component
class SaveCommandAdapter(
    private val saveRepository: SaveRepository,
    private val savePhotoRepository: SavePhotoRepository,
    private val saveTagRepository: SaveTagRepository,
    private val reviewRepository: ReviewRepository,
) : SaveCommandPort {
    override fun insertSave(
        userId: Long,
        placeId: Long,
        rating: Int?,
        content: String?,
    ): Long =
        saveRepository
            .save(
                SaveEntity(
                    userId = userId,
                    placeId = placeId,
                    rating = rating?.toShort(),
                    content = content,
                ),
            ).id

    override fun insertPhotos(
        saveId: Long,
        assetIds: List<Long>,
    ) {
        if (assetIds.isEmpty()) return
        savePhotoRepository.saveAll(
            assetIds.mapIndexed { index, assetId ->
                SavePhotoEntity(saveId = saveId, mediaAssetId = assetId, photoOrder = index.toShort())
            },
        )
    }

    override fun insertTags(
        saveId: Long,
        tagIds: Collection<String>,
    ) {
        if (tagIds.isEmpty()) return
        saveTagRepository.saveAll(tagIds.map { SaveTagEntity(SaveTagId(saveId = saveId, tagId = it)) })
    }

    override fun updateSave(
        saveId: Long,
        rating: Int?,
        content: String?,
    ) {
        saveRepository.updateContent(saveId, rating?.toShort(), content)
    }

    override fun deletePhotos(saveId: Long) {
        savePhotoRepository.deleteBySaveId(saveId)
    }

    override fun deleteTags(saveId: Long) {
        saveTagRepository.deleteBySaveId(saveId)
    }

    override fun deleteSave(saveId: Long): Int = saveRepository.deleteRow(saveId)

    override fun insertReview(
        saveId: Long,
        userId: Long,
        placeId: Long,
    ): Long = reviewRepository.save(ReviewEntity(saveId = saveId, userId = userId, placeId = placeId)).id
}
