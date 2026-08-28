package com.tmt.application.domain.media

import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class MediaAttachmentServiceTest {
    private val port = FakeMediaAssetPort()
    private val service = MediaAttachmentService(port, baseUrl = "https://media.tmt.example/")

    @Test
    fun `내가 발급받은 STAGED 사진은 붙일 수 있다`() {
        val id = port.seed(ownerId = 1)

        service.verifyAttachable(ownerId = 1, assetIds = listOf(id))
        service.attach(listOf(id))

        assertTrue(port.isAttached(id))
    }

    @Test
    fun `남의 사진이면 MEDIA_NOT_OWNED다 (M2)`() {
        val id = port.seed(ownerId = 99)

        val ex = assertThrows<TmtException> { service.verifyAttachable(ownerId = 1, assetIds = listOf(id)) }
        assertEquals(ErrorCode.MEDIA_NOT_OWNED, ex.errorCode)
    }

    @Test
    fun `없는 사진도 MEDIA_NOT_OWNED다 — 존재 여부를 흘리지 않는다`() {
        val ex = assertThrows<TmtException> { service.verifyAttachable(ownerId = 1, assetIds = listOf(404L)) }
        assertEquals(ErrorCode.MEDIA_NOT_OWNED, ex.errorCode)
    }

    @Test
    fun `이미 붙은 사진이면 MEDIA_ALREADY_ATTACHED다`() {
        val id = port.seed(ownerId = 1, attached = true)

        val ex = assertThrows<TmtException> { service.verifyAttachable(ownerId = 1, assetIds = listOf(id)) }
        assertEquals(ErrorCode.MEDIA_ALREADY_ATTACHED, ex.errorCode)
    }

    @Test
    fun `이어쓰기에서 원래 붙어 있던 사진은 다시 붙일 수 있다`() {
        val id = port.seed(ownerId = 1, attached = true)

        service.verifyAttachable(ownerId = 1, assetIds = listOf(id), reattachableIds = setOf(id))
        service.attach(listOf(id), reattachableIds = setOf(id))

        // 이미 ATTACHED라 건드리지 않는다 — 조건부 UPDATE가 0건이어도 경합이 아니다
        assertTrue(port.isAttached(id))
    }

    @Test
    fun `검증과 부착 사이에 남이 먼저 붙였으면 MEDIA_ALREADY_ATTACHED다 (TMT-177)`() {
        val id = port.seed(ownerId = 1)
        service.verifyAttachable(ownerId = 1, assetIds = listOf(id))

        // 경합 — 다른 요청이 먼저 전이시킨다
        port.markAttached(listOf(id))

        val ex = assertThrows<TmtException> { service.attach(listOf(id)) }
        assertEquals(ErrorCode.MEDIA_ALREADY_ATTACHED, ex.errorCode)
    }

    @Test
    fun `교체로 빠진 사진은 다시 STAGED가 된다 — 재부착·TTL 대상`() {
        val id = port.seed(ownerId = 1, attached = true)

        service.detach(listOf(id))

        assertFalse(port.isAttached(id))
    }

    @Test
    fun `조회 URL은 base-url과 s3Key를 잇는다 — 슬래시가 겹치지 않는다`() {
        val id = port.seed(ownerId = 1)

        assertEquals(mapOf(id to "https://media.tmt.example/review/$id.jpg"), service.urlsOf(listOf(id)))
    }

    @Test
    fun `빈 목록은 포트를 부르지 않고 지나간다`() {
        service.verifyAttachable(ownerId = 1, assetIds = emptyList())
        service.attach(emptyList())
        service.detach(emptyList())

        assertEquals(emptyMap<Long, String>(), service.urlsOf(emptyList()))
    }
}
