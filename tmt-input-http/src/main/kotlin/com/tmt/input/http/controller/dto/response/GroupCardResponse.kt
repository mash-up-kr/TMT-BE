package com.tmt.input.http.controller.dto.response

/** GroupCard (명세 v2 D_01 §2) — 그룹 목록·홈 추천 그룹이 같은 카드를 쓴다. */
data class GroupCardResponse(
    val groupId: String,
    val name: String,
    val oneLineDescription: String,
    val coverImageUrl: String?,
    val memberCount: Int,
    val reviewCount: Int,
    val placeCount: Int,
    /** 비로그인이면 0 (규약 §6-3). */
    val matchedSavedPlaceCount: Int,
)
