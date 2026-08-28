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

/**
 * 주소 검색 결과 1건. addressId 토큰에 통째로 실려 오가므로 저장소를 쓰지 않는다.
 * 주소 검색은 실구현으로 갈아탔고(TMT-192) 이제 `POST /v1/saves` mock만 이걸 쓴다 —
 * newPlace 실구현(TMT-193)에서 [MockAddressToken]과 함께 지운다.
 */
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

/** mock 사용자. 타인 프로필이 404를 낼 수 있으려면 존재하는 사용자 집합이 있어야 한다 (J-01 §6-2). */
data class MockUser(
    val userId: Long,
    val nickname: String,
    /** 카카오 동의 항목을 못 받으면 null (J §2). 타인 프로필 응답에는 실리지 않는다 (U7). */
    val email: String?,
    val profileImageUrl: String? = null,
)

/** 티켓 이력 한 행 (T10). 발급·소비·회수가 한 목록이고 amount 부호로 구분한다. */
data class MockTicketEntry(
    val entryId: String,
    val userId: Long,
    val type: TicketEntryType,
    val amount: Int,
    val saveId: String?,
    val placeId: String?,
    val groupId: String?,
    val occurredAt: Instant,
)

/** 미완성 저장(SAVE_IN_PROGRESS)은 저장에서 파생하므로 여기 없다 — amount가 null인 행이다. */
enum class TicketEntryType {
    SIGNUP_REWARD,
    REVIEW_REWARD,
    REVIEW_DELETE_REVOKE,
    GROUP_JOIN,
}
