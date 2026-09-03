package com.tmt.application.port.output.persistence

import java.time.Instant

/**
 * 리뷰 단건 읽기 (I §6-3·§6-4). 두 조회 모두 삭제된 리뷰를 제외한다 — 지워진 리뷰는
 * 없는 리뷰와 같다 (D6).
 */
interface ReviewQueryPort {
    /** 상세 본체. 사진·태그·요약은 [ReviewCardLookupPort]가 붙인다. */
    fun findReviewDetail(reviewId: Long): ReviewDetailRow?

    /** 삭제에 필요한 최소 정보. 소유자 판정과 되돌릴 집계 값이 여기서 온다. */
    fun findReviewForDeletion(reviewId: Long): ReviewDeletionRow?
}

data class ReviewDetailRow(
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
    val placeRoadAddress: String,
    /** place.category_id (14종 상수 코드) — 매핑 실패 매장은 null (E11) */
    val placeCategoryId: String?,
)

data class ReviewDeletionRow(
    val reviewId: Long,
    val saveId: Long,
    val userId: Long,
    val placeId: Long,
    /** 매장 집계를 되돌릴 값 — 리뷰가 성립했으므로 항상 있다 (C4). */
    val rating: Int,
)

/** 리뷰 삭제 쓰기 (I §6-4). 호출부가 연 트랜잭션에 참여한다 (TX-5). */
interface ReviewCommandPort {
    /**
     * `save_photo` 행을 지우고 붙어 있던 media_asset id를 돌려준다. `media_asset`을 지우려면
     * 참조하는 `save_photo`가 먼저 사라져야 한다 — 스키마에 CASCADE가 없다 (F §7).
     */
    fun deletePhotoLinks(saveId: Long): List<Long>

    /** 조건부 UPDATE. 이미 삭제된 행이면 0을 돌려준다 — 시각을 덮어쓰지 않는다 (D6). */
    fun softDeleteReview(reviewId: Long): Int

    fun softDeleteSave(saveId: Long): Int
}
