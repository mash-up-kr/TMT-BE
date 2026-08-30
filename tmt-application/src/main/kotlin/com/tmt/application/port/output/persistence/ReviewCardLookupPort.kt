package com.tmt.application.port.output.persistence

import java.time.Instant

/**
 * ReviewCard 조립 재료 (TMT-180 카드 공용화의 읽기 쪽). 근처 피드·가게 상세·홈이
 * 같은 카드를 쓰므로 부속(사진·태그·요약) 배치 조회도 여기 한 곳이다.
 */
interface ReviewCardLookupPort {
    fun findPhotoRows(saveIds: Collection<Long>): List<PhotoRow>

    fun findTagRows(saveIds: Collection<Long>): List<TagRow>

    fun findSummaryRows(reviewIds: Collection<Long>): List<SummaryRow>
}

/** 카드 한 장의 본체 행 — 목록 쿼리(근처·가게 상세·홈)가 이 모양으로 돌려준다. */
data class ReviewCardRow(
    val reviewId: Long,
    val saveId: Long,
    val createdAt: Instant,
    val rating: Int,
    val content: String,
    val authorId: Long,
    val authorNickname: String,
    val authorProfileImageUrl: String?,
    val placeId: Long,
    val placeName: String,
    val placeRegionName: String,
    /** place.category_id (14종 상수 코드) — 매핑 실패 매장은 null (E11) */
    val placeCategoryId: String?,
    /** 좌표 파라미터가 없는 목록에서는 null (규약 §6-3) */
    val distanceMeters: Int?,
    val favorite: Boolean,
)

data class PhotoRow(
    val saveId: Long,
    val savePhotoId: Long,
    val s3Key: String,
    val photoOrder: Int,
)

data class TagRow(
    val saveId: Long,
    val tagId: String,
    val label: String,
)

data class SummaryRow(
    val reviewId: Long,
    val pros: String?,
    val cons: String?,
)
