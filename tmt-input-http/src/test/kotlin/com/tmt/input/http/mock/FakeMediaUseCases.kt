package com.tmt.input.http.mock

import com.tmt.application.port.input.AttachMediaUseCase
import com.tmt.application.port.input.GetMediaUrlsUseCase
import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException

/**
 * mock 컨트롤러 테스트가 쓰는 미디어 유스케이스 대역 (TMT-202 과도기).
 * 실 서비스 동작(M2·M3)을 인메모리로 흉내낸다 — 소유자 검증과 부착 상태만 본다.
 * mock 컨트롤러가 사라질 때 이 파일도 함께 지운다.
 */
class FakeAttachMediaUseCase : AttachMediaUseCase {
    private val ownerById = mutableMapOf<Long, Long>()
    private val attached = mutableSetOf<Long>()

    /** 발급된 사진 한 장을 만든다 — 실구현 assetId는 숫자다. */
    fun issue(
        assetId: Long,
        ownerId: Long = 1,
    ): String {
        ownerById[assetId] = ownerId
        return assetId.toString()
    }

    fun isAttached(assetId: Long): Boolean = assetId in attached

    override fun verifyAttachable(
        ownerId: Long,
        assetIds: List<Long>,
        reattachableIds: Set<Long>,
    ) {
        assetIds.forEach { id ->
            if (ownerById[id] != ownerId) throw TmtException(ErrorCode.MEDIA_NOT_OWNED, id.toString())
            if (id in attached && id !in reattachableIds) {
                throw TmtException(ErrorCode.MEDIA_ALREADY_ATTACHED, id.toString())
            }
        }
    }

    override fun attach(
        assetIds: List<Long>,
        reattachableIds: Set<Long>,
    ) {
        attached += assetIds
    }

    override fun detach(assetIds: List<Long>) {
        attached -= assetIds.toSet()
    }
}

/** 실발급분의 조회 URL 대역. 실 어댑터와 같은 형태(버킷 base-url + 키)면 충분하다. */
class FakeGetMediaUrlsUseCase : GetMediaUrlsUseCase {
    override fun urlsOf(assetIds: List<Long>): Map<Long, String> =
        assetIds.associateWith { "https://media.test.tmt/review/$it.jpg" }
}

/** mock 응답의 사진 URL 브리지 — 실발급분은 대역 URL, 시드(`asset_*`)는 가짜 CDN. */
fun fakeMockMediaUrls(): MockMediaUrls = MockMediaUrls(FakeGetMediaUrlsUseCase())
