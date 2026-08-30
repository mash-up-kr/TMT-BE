package com.tmt.application.domain.save

import com.tmt.application.domain.media.MediaUrlFactory
import com.tmt.application.domain.place.FoodCategories
import com.tmt.application.port.input.GetSaveUseCase
import com.tmt.application.port.input.ListMySavesUseCase
import com.tmt.application.port.input.MySaveView
import com.tmt.application.port.input.MySavesRequest
import com.tmt.application.port.input.MySavesResult
import com.tmt.application.port.input.SaveDetailView
import com.tmt.application.port.output.persistence.SaveQueryPort
import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 본인 상세(I §6-2)와 이어쓰기 목록(G §5-1). */
@Service
@Transactional(readOnly = true)
class SaveQueryService(
    private val saveQueryPort: SaveQueryPort,
    private val mediaUrlFactory: MediaUrlFactory,
) : GetSaveUseCase,
    ListMySavesUseCase {
    override fun get(
        userId: Long,
        saveId: Long,
    ): SaveDetailView {
        val save = saveQueryPort.findSave(saveId) ?: throw TmtException(ErrorCode.SAVE_NOT_FOUND)
        // 소유자에게만 응답한다 (S8)
        if (save.userId != userId) throw TmtException(ErrorCode.FORBIDDEN)

        return SaveDetailView(
            saveId = save.saveId,
            reviewId = save.reviewId,
            place =
                SaveDetailView.Place(
                    placeId = save.placeId,
                    name = save.placeName,
                    roadAddress = save.placeRoadAddress,
                    categoryName = FoodCategories.labelOf(save.placeCategoryId),
                ),
            photos =
                saveQueryPort.findSavePhotos(saveId).map {
                    SaveDetailView.Photo(
                        photoId = it.savePhotoId,
                        url = mediaUrlFactory.of(it.s3Key),
                        order = it.photoOrder,
                    )
                },
            tags = saveQueryPort.findSaveTags(saveId).map { SaveDetailView.Tag(it.tagId, it.label) },
            rating = save.rating,
            content = save.content,
            // 리뷰가 아니거나 요약이 아직 없으면 null (A2)
            aiSummary =
                save.reviewId?.let {
                    if (save.aiSummaryPros == null && save.aiSummaryCons == null) {
                        null
                    } else {
                        SaveDetailView.AiSummary(save.aiSummaryPros, save.aiSummaryCons)
                    }
                },
            createdAt = save.createdAt,
        )
    }

    override fun list(request: MySavesRequest): MySavesResult {
        val page =
            saveQueryPort.findMySaveRows(
                userId = request.userId,
                afterUpdatedAt = request.after?.updatedAt,
                afterSaveId = request.after?.saveId,
                limit = request.limit,
            )
        return MySavesResult(
            items =
                page.rows.map {
                    MySaveView(
                        saveId = it.saveId,
                        placeId = it.placeId,
                        placeName = it.placeName,
                        placeRoadAddress = it.placeRoadAddress,
                        thumbnailUrl = it.thumbnailS3Key?.let(mediaUrlFactory::of),
                        updatedAt = it.updatedAt,
                    )
                },
            hasNext = page.hasNext,
        )
    }
}
