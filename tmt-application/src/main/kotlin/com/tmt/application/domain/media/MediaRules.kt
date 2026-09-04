package com.tmt.application.domain.media

/**
 * 미디어 업로드 규칙 (M3). 정본은 여기다 — mock의 `ReviewFormRules`가 이 값을 참조한다
 * (mock은 한시적이라 반대 방향 참조는 금지).
 */
object MediaRules {
    /** 사진 한 장 최대 5MiB (M3) */
    const val MAX_CONTENT_LENGTH = 5_242_880L

    /**
     * contentType → S3 키 확장자. 허용 목록이자 확장자 결정표다 (M3).
     *
     * `image/heic`는 **iOS 카메라 기본 포맷**이라 빼면 아이폰 사용자가 원본을 그대로 못 올린다
     * (M8 확정, TMT-349). presigned 발급이 받은 contentType을 그대로 넘기므로 S3 쪽에 따로
     * 허용 목록이 없다 — 여기가 유일한 관문이다.
     */
    val EXTENSION_BY_CONTENT_TYPE =
        mapOf(
            "image/jpeg" to "jpg",
            "image/png" to "png",
            "image/webp" to "webp",
            "image/heic" to "heic",
        )

    val ALLOWED_CONTENT_TYPES: Set<String> = EXTENSION_BY_CONTENT_TYPE.keys
}
