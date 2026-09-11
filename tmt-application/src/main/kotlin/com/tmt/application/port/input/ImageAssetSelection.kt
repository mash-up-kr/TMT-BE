package com.tmt.application.port.input

/**
 * 이미지 지정 — 전체 교체 요청에서도 생략(Keep)은 현재 이미지를 그대로 두고,
 * 명시적 없음(None)만 지운다 (TMT-415). 그룹 대표 이미지·프로필 사진이 함께 쓴다.
 */
sealed interface ImageAssetSelection {
    data object Keep : ImageAssetSelection

    data object None : ImageAssetSelection

    data class Set(
        val assetId: Long,
    ) : ImageAssetSelection
}
