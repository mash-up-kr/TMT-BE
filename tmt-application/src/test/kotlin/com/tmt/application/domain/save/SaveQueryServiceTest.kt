package com.tmt.application.domain.save

import com.tmt.application.domain.media.MediaRules
import com.tmt.application.domain.media.MediaUrlFactory
import com.tmt.application.port.input.MySavesRequest
import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SaveQueryServiceTest {
    private val saveCommandPort = FakeSaveCommandPort()
    private val saveQueryPort = FakeSaveQueryPort(saveCommandPort)
    private val service = SaveQueryService(saveQueryPort, MediaUrlFactory("https://media.tmt.example/"))
    private val reviewTagPort = FakeReviewTagPort()

    private fun seed(
        userId: Long = 1,
        photoAssetIds: List<Long> = emptyList(),
    ): Long {
        val saveId = saveCommandPort.insertSave(userId = userId, placeId = 1, rating = 4, content = "맛있어요")
        saveCommandPort.insertPhotos(saveId, photoAssetIds)
        saveCommandPort.insertTags(saveId, listOf("tag_couple"))
        return saveId
    }

    @Test
    fun `본인 상세는 사진 URL과 매장 카테고리명을 함께 준다`() {
        val saveId = seed(photoAssetIds = listOf(7L))

        val detail = service.get(userId = 1, saveId = saveId)

        assertEquals("https://media.tmt.example/photos/7.jpg", detail.photos.single().url)
        assertEquals("한식", detail.place.categoryName)
        assertEquals(4, detail.rating)
    }

    @Test
    fun `남의 저장 조회는 없는 저장과 같게 404다 (S8)`() {
        val saveId = seed(userId = 1)

        assertEquals(
            ErrorCode.SAVE_NOT_FOUND,
            assertThrows<TmtException> { service.get(userId = 2, saveId = saveId) }.errorCode,
        )
    }

    @Test
    fun `없는 저장 조회는 SAVE_NOT_FOUND다`() {
        assertEquals(
            ErrorCode.SAVE_NOT_FOUND,
            assertThrows<TmtException> { service.get(userId = 1, saveId = 999) }.errorCode,
        )
    }

    @Test
    fun `이어쓰기 목록은 본인 것만 내려간다`() {
        val mine = seed(userId = 1)
        seed(userId = 2)

        val result = service.list(MySavesRequest(userId = 1, after = null, limit = 20))

        assertEquals(listOf(mine), result.items.map { it.saveId })
        assertEquals(false, result.hasNext)
    }

    @Test
    fun `review-form-config는 시드 태그와 서버 상수를 그대로 내려준다`() {
        val config = ReviewFormConfigService(reviewTagPort).get()

        assertEquals(SaveRules.PHOTO_MAX_COUNT, config.photoMaxCount)
        assertEquals(MediaRules.MAX_CONTENT_LENGTH, config.photoMaxBytes)
        assertEquals(SaveRules.CONTENT_MAX_LENGTH, config.contentMaxLength)
        assertEquals(SaveRules.RATING_MIN to SaveRules.RATING_MAX, config.ratingMin to config.ratingMax)
        assertEquals(5, config.companionTags.size)
        assertEquals(7, config.positivePointTags.size)
    }
}
