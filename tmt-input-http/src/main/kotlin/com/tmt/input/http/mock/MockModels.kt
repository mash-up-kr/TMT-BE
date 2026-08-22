package com.tmt.input.http.mock

import java.time.Instant

data class MockPlace(
    val placeId: String,
    val name: String,
    val roadAddress: String,
    val regionName: String,
    val categoryName: String?,
    val latitude: Double,
    val longitude: Double,
    // 공공데이터에 결측이 많아 null이 흔하다 (B §3-1) — 시드도 일부만 채운다
    val phoneNumber: String? = null,
)

/** 주소 검색 결과 1건. addressId 토큰에 통째로 실려 오가므로 저장소를 쓰지 않는다. */
data class MockAddress(
    val roadAddress: String,
    val jibunAddress: String,
    val regionName: String,
    val latitude: Double,
    val longitude: Double,
    /** false면 POST /v1/saves가 ADDRESS_NOT_FOUND를 낸다 — 좌표 확보 실패 재현용 */
    val hasCoordinate: Boolean = true,
)

data class MockAsset(
    val assetId: String,
    val ownerId: Long,
    val contentType: String,
    val attached: Boolean = false,
)

data class MockSave(
    val saveId: String,
    val ownerId: Long,
    val placeId: String,
    val photoAssetIds: List<String>,
    val companionTagIds: List<String>,
    val positivePointTagIds: List<String>,
    val rating: Int?,
    val content: String?,
    val reviewId: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class MockGroup(
    val groupId: String,
    val name: String,
    val oneLineDescription: String,
    val description: String?,
    val imageAssetId: String?,
    val foodCategoryId: String,
    val regionTagIds: List<String>,
    val ownerId: Long,
    val createdAt: Instant,
)
