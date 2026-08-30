package com.tmt.application.domain.review

import com.tmt.application.domain.place.FoodCategories
import com.tmt.application.port.input.GetReviewDetailUseCase
import com.tmt.application.port.input.ReviewDetailView
import com.tmt.application.port.output.persistence.ReviewCardLookupPort
import com.tmt.application.port.output.persistence.ReviewQueryPort
import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 공개 리뷰 상세 (I §6-3, TMT-226). 사진·태그·요약은 카드와 같은 배치 조회를 재사용한다
 * (TMT-180) — 상세와 카드가 서로 다른 순서·URL 규칙을 갖지 않게.
 */
@Service
class ReviewDetailService(
    private val reviewQueryPort: ReviewQueryPort,
    private val reviewCardLookupPort: ReviewCardLookupPort,
    private val reviewCardComposer: ReviewCardComposer,
) : GetReviewDetailUseCase {
    @Transactional(readOnly = true)
    override fun get(
        viewerId: Long?,
        reviewId: Long,
    ): ReviewDetailView {
        // 미완성 저장은 review 행이 없어 이 조회에 걸리지 않는다 (R8)
        val row = reviewQueryPort.findReviewDetail(reviewId) ?: throw TmtException(ErrorCode.REVIEW_NOT_FOUND)
        val saveIds = listOf(row.saveId)

        return ReviewDetailView(
            reviewId = row.reviewId,
            author =
                ReviewDetailView.Author(
                    userId = row.authorId,
                    nickname = row.authorNickname,
                    profileImageUrl = row.authorProfileImageUrl,
                ),
            place =
                ReviewDetailView.Place(
                    placeId = row.placeId,
                    name = row.placeName,
                    roadAddress = row.placeRoadAddress,
                    categoryName = FoodCategories.labelOf(row.placeCategoryId),
                ),
            photos =
                reviewCardLookupPort.findPhotoRows(saveIds).sortedBy { it.photoOrder }.map {
                    ReviewDetailView.Photo(
                        photoId = it.savePhotoId,
                        url = reviewCardComposer.mediaUrl(it.s3Key),
                        order = it.photoOrder,
                    )
                },
            tags = reviewCardLookupPort.findTagRows(saveIds).map { ReviewDetailView.Tag(it.tagId, it.label) },
            rating = row.rating,
            content = row.content,
            aiSummary =
                reviewCardLookupPort
                    .findSummaryRows(listOf(reviewId))
                    .firstOrNull()
                    ?.let { ReviewDetailView.AiSummary(it.pros, it.cons) },
            isMine = viewerId != null && viewerId == row.authorId,
            createdAt = row.createdAt,
        )
    }
}
