package com.tmt.application.port.output.persistence

/**
 * 매장의 리뷰 수·별점 합계를 갱신한다. 평균 별점은 `rating_sum / review_count`로 파생한다.
 *
 * 구현은 조건부 UPDATE 한 문장이어야 한다. 읽어서 더하고 쓰면 같은 매장에 리뷰가 동시에
 * 달릴 때 한쪽이 사라진다.
 */
interface PlaceStatsPort {
    /** 리뷰 1건이 완성됐을 때. */
    fun addReview(
        placeId: Long,
        rating: Int,
    )

    /** 리뷰 1건이 삭제됐을 때. 같은 `rating`으로 되돌린다. */
    fun removeReview(
        placeId: Long,
        rating: Int,
    )
}
