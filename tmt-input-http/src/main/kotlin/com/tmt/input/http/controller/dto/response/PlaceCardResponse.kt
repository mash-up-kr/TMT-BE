package com.tmt.input.http.controller.dto.response

/** PlaceCard (명세 v2 B §2-2) — 매장 검색·지도 핀 시트가 같은 카드를 쓴다. */
data class PlaceCardResponse(
    val placeId: String,
    val name: String,
    val roadAddress: String,
    val regionName: String,
    /** 카테고리 아이콘 키. 14종 매핑에 실패하면 null (E11). */
    val categoryId: String?,
    /** 14종 매핑에 실패하면 null (E11). */
    val categoryName: String?,
    /** 리뷰가 없으면 null. 소수 첫째 자리 (규약 §8-3). */
    val averageRating: Double?,
    val reviewCount: Int,
    val thumbnailUrl: String?,
    val distanceMeters: Int?,
    val isFavorite: Boolean,
)
