package com.tmt.input.http.controller.dto.response

/** 유저 활동량 랭킹 한 줄 (TMT-436). */
data class UserRankingResponse(
    val userId: String,
    val nickname: String,
    val profileImageUrl: String?,
    val reviewCount: Int,
    /** 그룹에 공유한 리뷰 수. 한 리뷰를 여러 그룹에 공유해도 1이다. */
    val sharedReviewCount: Int,
)
