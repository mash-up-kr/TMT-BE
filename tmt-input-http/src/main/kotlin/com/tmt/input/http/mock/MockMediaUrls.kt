package com.tmt.input.http.mock

import com.tmt.application.port.input.GetMediaUrlsUseCase
import org.springframework.stereotype.Component

/**
 * mock 응답의 사진 URL 브리지 (TMT-202 과도기). 실구현 발급분(숫자 assetId)은 실제
 * 버킷 URL을, 부팅 시드(`asset_*`)는 예전 가짜 CDN 주소를 그대로 내린다 —
 * 시드는 S3에 실객체가 없어서 어차피 실URL을 만들 수 없다.
 *
 * 사진은 항목당 최대 3장이라(M3) 단건 조회의 N+1은 mock 수명 동안 감수한다.
 * mock이 사라질 때 이 파일과 [mockMediaUrl]도 함께 지운다.
 */
@Component
class MockMediaUrls(
    private val getMediaUrlsUseCase: GetMediaUrlsUseCase,
) {
    fun urlOf(assetId: String): String =
        assetId.toLongOrNull()?.let { id -> getMediaUrlsUseCase.urlsOf(listOf(id))[id] }
            ?: mockMediaUrl(assetId)
}

/** 부팅 시드 사진의 가짜 CDN 주소 — 실객체가 없는 시드 전용이다. */
fun mockMediaUrl(assetId: String): String = "https://mock-cdn.tmt.example/$assetId.jpg"
