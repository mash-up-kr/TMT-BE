package com.tmt.output.persistence.postgres.adapter

import com.tmt.application.port.output.persistence.MySaveRow
import com.tmt.application.port.output.persistence.MySaveRows
import com.tmt.application.port.output.persistence.SavePhotoRow
import com.tmt.application.port.output.persistence.SaveQueryPort
import com.tmt.application.port.output.persistence.SaveRow
import com.tmt.application.port.output.persistence.SaveTagRow
import com.tmt.output.persistence.postgres.repository.SaveQueryRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Component
@Transactional(readOnly = true)
class SaveQueryAdapter(
    private val saveQueryRepository: SaveQueryRepository,
) : SaveQueryPort {
    override fun findSave(saveId: Long): SaveRow? =
        saveQueryRepository.findSaveRow(saveId)?.let {
            SaveRow(
                saveId = it.getSaveId(),
                userId = it.getUserId(),
                reviewId = it.getReviewId(),
                rating = it.getRating(),
                content = it.getContent(),
                createdAt = it.getCreatedAt(),
                placeId = it.getPlaceId(),
                placeName = it.getPlaceName(),
                placeRoadAddress = it.getPlaceRoadAddress(),
                placeCategoryId = it.getPlaceCategoryId(),
                aiSummaryPros = it.getAiSummaryPros(),
                aiSummaryCons = it.getAiSummaryCons(),
            )
        }

    override fun findSavePhotos(saveId: Long): List<SavePhotoRow> =
        saveQueryRepository.findPhotoRows(saveId).map {
            SavePhotoRow(savePhotoId = it.getSavePhotoId(), s3Key = it.getS3Key(), photoOrder = it.getPhotoOrder())
        }

    override fun findSaveTags(saveId: Long): List<SaveTagRow> =
        saveQueryRepository.findTagRows(saveId).map { SaveTagRow(tagId = it.getTagId(), label = it.getLabel()) }

    override fun findPhotoAssetIds(saveId: Long): List<Long> =
        saveQueryRepository.findPhotoRows(saveId).map { it.getMediaAssetId() }

    override fun findMySaveRows(
        userId: Long,
        afterUpdatedAt: Instant?,
        afterSaveId: Long?,
        limit: Int,
    ): MySaveRows {
        // 한 건 더 읽어 다음 페이지 존재를 판정한다 (규약 §5-2)
        val rows = saveQueryRepository.findMySaveRows(userId, afterUpdatedAt, afterSaveId, limit + 1)
        return MySaveRows(
            rows =
                rows.take(limit).map {
                    MySaveRow(
                        saveId = it.getSaveId(),
                        placeId = it.getPlaceId(),
                        placeName = it.getPlaceName(),
                        placeRoadAddress = it.getPlaceRoadAddress(),
                        thumbnailS3Key = it.getThumbnailS3Key(),
                        updatedAt = it.getUpdatedAt(),
                    )
                },
            hasNext = rows.size > limit,
        )
    }
}
