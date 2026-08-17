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

data class MockAddress(
    val addressId: String,
    val roadAddress: String,
    val jibunAddress: String,
    val latitude: Double,
    val longitude: Double,
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
