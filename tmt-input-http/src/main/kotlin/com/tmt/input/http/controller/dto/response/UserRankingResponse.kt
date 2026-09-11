package com.tmt.input.http.controller.dto.response

/** 유저 활동량 랭킹 한 줄 (TMT-436). */
data class UserRankingResponse(
    val userId: String,
    val nickname: String,
    val profileImageUrl: String?,
    val reviewCount: Int,
    /** 소유한 그룹의 멤버 수 합(생성자 포함). 소유 그룹이 없으면 0. */
    val memberCount: Int,
)
