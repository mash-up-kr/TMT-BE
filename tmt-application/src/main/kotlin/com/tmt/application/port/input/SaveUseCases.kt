package com.tmt.application.port.input

/**
 * 작성 완료 (F §4-1). 저장/리뷰 구분은 서버의 완성도 판정(C4)이 하고 클라이언트는 보내지 않는다 (C7).
 */
interface CreateSaveUseCase {
    fun create(command: CreateSaveCommand): SaveResult
}

/**
 * 매장은 기존 것을 고르거나 직접 등록하거나 둘 중 하나다 — 정확히 하나만 온다 (C1, F §4-1).
 *
 * [New]는 **이미 해석이 끝난 값**이다. addressId 서명 검증과 좌표 API 호출은 트랜잭션 밖에서
 * 끝나 있어야 한다 — 외부 I/O를 트랜잭션에 넣지 않는다.
 */
sealed interface PlaceSelection {
    data class Existing(
        val placeId: Long,
    ) : PlaceSelection

    data class New(
        val name: String,
        val roadAddress: String,
        val jibunAddress: String?,
        val regionName: String,
        val categoryId: String?,
        val latitude: Double,
        val longitude: Double,
    ) : PlaceSelection
}

data class CreateSaveCommand(
    val userId: Long,
    val place: PlaceSelection,
    val photoAssetIds: List<Long>,
    val companionTagIds: List<String>,
    val positivePointTagIds: List<String>,
    val rating: Int?,
    val content: String?,
)

/**
 * @param reviewId null이면 저장, 값이 있으면 리뷰다 (S3). 화면 분기의 유일한 기준.
 * @param placeId newPlace로 만들어진 매장의 ID. 기존 매장이면 요청값과 같다.
 * @param grantedCount 이번 요청으로 발급된 티켓 수 (0 또는 1). 상한 999장이면 리뷰여도 0이다 (T6).
 */
data class SaveResult(
    val saveId: Long,
    val reviewId: Long?,
    val placeId: Long,
    val grantedCount: Int,
    val availableCount: Int,
)
