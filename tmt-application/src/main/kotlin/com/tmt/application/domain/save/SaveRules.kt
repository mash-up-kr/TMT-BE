package com.tmt.application.domain.save

import com.tmt.application.port.input.ReviewCriterion

/** 폼 제약(F §3-1)과 리뷰 성립 판정(C4)의 서버 상수. review-form-config 응답과 같은 값이어야 한다. */
object SaveRules {
    const val PHOTO_MAX_COUNT = 3
    const val RATING_MIN = 1
    const val RATING_MAX = 5

    /** 별점은 1점 단위다 — review-form-config가 그대로 내려준다. */
    const val RATING_STEP = 1
    const val CONTENT_MAX_LENGTH = 500

    /** 보유 상한 (T6). 상한에 닿으면 리뷰가 성립해도 티켓이 나가지 않는다. */
    const val TICKET_MAX_AVAILABLE = 999

    /**
     * 동행 1 + 좋은 점 1 + 별점 + 본문(공백 제외 1자 이상)을 전부 충족해야 리뷰다 (C4).
     * 사진은 판정에 넣지 않는다 — 0장이어도 리뷰다 (C4-1).
     *
     * 모자란 항목을 선언 순서대로 돌려주고, **비어 있으면 리뷰다** — 판정의 정본은 이 함수 하나다.
     * 저장 응답의 `missing`이 되어 "별점만 매기면 리뷰가 돼요" 같은 안내의 근거가 된다 (TMT-395).
     */
    fun missingReviewCriteria(
        companionTagCount: Int,
        positivePointTagCount: Int,
        rating: Int?,
        content: String?,
    ): List<ReviewCriterion> =
        buildList {
            if (companionTagCount <= 0) add(ReviewCriterion.COMPANION_TAG)
            if (positivePointTagCount <= 0) add(ReviewCriterion.POSITIVE_POINT_TAG)
            if (rating == null) add(ReviewCriterion.RATING)
            if (content.isNullOrBlank()) add(ReviewCriterion.CONTENT)
        }

    /**
     * 리뷰·티켓·집계를 내보낼지의 판정이다 — 최초 저장과 이어쓰기가 같은 기준을 쓴다 (C6).
     *
     * 중간 저장은 [missing]이 비어 있어도 승격하지 않는다 — 사용자가 `작성 완료`를 누른 적이 없다 (TMT-426).
     */
    fun shouldPromote(
        draft: Boolean,
        missing: List<ReviewCriterion>,
    ): Boolean = !draft && missing.isEmpty()
}
