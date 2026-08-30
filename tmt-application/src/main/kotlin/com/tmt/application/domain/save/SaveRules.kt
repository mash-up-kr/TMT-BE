package com.tmt.application.domain.save

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

    /** 사진 1 + 동행 1 + 좋은 점 1 + 별점 + 본문(공백 제외 1자 이상)을 전부 충족해야 리뷰다 (C4). */
    fun satisfiesReviewCriteria(
        photoCount: Int,
        companionTagCount: Int,
        positivePointTagCount: Int,
        rating: Int?,
        content: String?,
    ): Boolean =
        photoCount > 0 &&
            companionTagCount > 0 &&
            positivePointTagCount > 0 &&
            rating != null &&
            !content.isNullOrBlank()
}
