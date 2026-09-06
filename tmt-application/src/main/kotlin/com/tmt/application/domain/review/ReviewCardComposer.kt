package com.tmt.application.domain.review

import com.tmt.application.domain.media.MediaUrlResolver
import com.tmt.application.domain.place.FoodCategories
import com.tmt.application.port.input.ReviewCardView
import com.tmt.application.port.output.persistence.ReviewCardLookupPort
import com.tmt.application.port.output.persistence.ReviewCardRow
import org.springframework.stereotype.Component

/**
 * ReviewCard 조립 (B §1-1) — 본체 행에 사진·태그·AI 요약을 배치 조회로 붙인다.
 * 근처 피드·가게 상세·홈이 같은 카드를 쓴다 (TMT-180).
 */
@Component
class ReviewCardComposer(
    private val reviewCardLookupPort: ReviewCardLookupPort,
    private val mediaUrlResolver: MediaUrlResolver,
) {
    fun compose(rows: List<ReviewCardRow>): List<ReviewCardView> {
        if (rows.isEmpty()) return emptyList()

        val saveIds = rows.map { it.saveId }
        val photosBySave = reviewCardLookupPort.findPhotoRows(saveIds).groupBy { it.saveId }
        val tagsBySave = reviewCardLookupPort.findTagRows(saveIds).groupBy { it.saveId }
        val summaryByReview = reviewCardLookupPort.findSummaryRows(rows.map { it.reviewId }).associateBy { it.reviewId }

        return rows.map { row ->
            ReviewCardView(
                reviewId = row.reviewId,
                authorId = row.authorId,
                authorNickname = row.authorNickname,
                authorProfileImageUrl = row.authorProfileImageUrl,
                rating = row.rating,
                distanceMeters = row.distanceMeters,
                photos =
                    photosBySave[row.saveId].orEmpty().sortedBy { it.photoOrder }.map { photo ->
                        ReviewCardView.Photo(
                            photoId = photo.savePhotoId,
                            // 공개 읽기 버킷 (TMT-201) — base-url + s3_key가 곧 조회 URL이다
                            url = mediaUrl(photo.s3Key),
                            order = photo.photoOrder,
                        )
                    },
                aiSummary = summaryByReview[row.reviewId]?.let { ReviewCardView.AiSummary(it.pros, it.cons) },
                content = row.content,
                tags = tagsBySave[row.saveId].orEmpty().map { ReviewCardView.Tag(it.tagId, it.label) },
                placeId = row.placeId,
                placeName = row.placeName,
                placeRegionName = row.placeRegionName,
                placeCategoryId = row.placeCategoryId,
                placeCategoryName = FoodCategories.labelOf(row.placeCategoryId),
                placeFavorite = row.favorite,
                createdAt = row.createdAt,
            )
        }
    }

    fun mediaUrl(s3Key: String): String = mediaUrlResolver.urlOf(s3Key)
}
