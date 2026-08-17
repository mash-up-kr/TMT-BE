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
