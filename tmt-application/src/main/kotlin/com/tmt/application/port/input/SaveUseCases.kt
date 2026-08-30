package com.tmt.application.port.input

/**
 * 작성 완료 (F §4-1). 저장/리뷰 구분은 서버의 완성도 판정(C4)이 하고 클라이언트는 보내지 않는다 (C7).
 * 매장 직접 등록(newPlace) 경로는 TMT-193에서 이 위에 얹는다.
 */
interface CreateSaveUseCase {
    fun create(command: CreateSaveCommand): SaveResult
}

data class CreateSaveCommand(
    val userId: Long,
    val placeId: Long,
    val photoAssetIds: List<Long>,
    val companionTagIds: List<String>,
    val positivePointTagIds: List<String>,
    val rating: Int?,
    val content: String?,
)

/**
 * @param reviewId null이면 저장, 값이 있으면 리뷰다 (S3). 화면 분기의 유일한 기준.
 * @param grantedCount 이번 요청으로 발급된 티켓 수 (0 또는 1). 상한 999장이면 리뷰여도 0이다 (T6).
 */
data class SaveResult(
    val saveId: Long,
    val reviewId: Long?,
    val placeId: Long,
    val grantedCount: Int,
    val availableCount: Int,
)
