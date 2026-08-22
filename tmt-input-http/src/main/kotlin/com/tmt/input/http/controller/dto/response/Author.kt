package com.tmt.input.http.controller.dto.response

/** 리뷰 카드·리뷰 상세가 같은 작성자 표현을 쓴다. */
data class Author(
    val userId: String,
    val nickname: String,
    val profileImageUrl: String?,
)
