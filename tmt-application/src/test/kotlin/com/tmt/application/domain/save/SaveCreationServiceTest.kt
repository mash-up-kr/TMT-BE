package com.tmt.application.domain.save

import com.tmt.application.domain.aisummary.ReviewCommittedEvent
import com.tmt.application.domain.media.FakeMediaAssetPort
import com.tmt.application.domain.media.MediaAttachmentService
import com.tmt.application.port.input.CreateSaveCommand
import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.context.ApplicationEventPublisher

class SaveCreationServiceTest {
    private val saveCommandPort = FakeSaveCommandPort()
    private val placeQueryPort = FakePlaceQueryPort(existingPlaceIds = setOf(1L))
    private val reviewTagPort = FakeReviewTagPort()
    private val mediaAssetPort = FakeMediaAssetPort()
    private val ticketPort = FakeGroupJoinTicketPort()
    private val placeStatsPort = FakePlaceStatsPort()
    private val published = mutableListOf<Any>()

    private val service =
        SaveCreationService(
            saveCommandPort = saveCommandPort,
            placeQueryPort = placeQueryPort,
            reviewTagPort = reviewTagPort,
            attachMediaUseCase = MediaAttachmentService(mediaAssetPort, baseUrl = "https://media.tmt.example"),
            groupJoinTicketPort = ticketPort,
            placeStatsPort = placeStatsPort,
            eventPublisher = ApplicationEventPublisher { event -> published += event },
        )

    private fun command(
        userId: Long = 1,
        placeId: Long = 1,
        photoAssetIds: List<Long> = emptyList(),
        companionTagIds: List<String> = emptyList(),
        positivePointTagIds: List<String> = emptyList(),
        rating: Int? = null,
        content: String? = null,
    ) = CreateSaveCommand(userId, placeId, photoAssetIds, companionTagIds, positivePointTagIds, rating, content)

    private fun completeCommand(userId: Long = 1): CreateSaveCommand {
        val assetId = mediaAssetPort.seed(ownerId = userId)
        return command(
            userId = userId,
            photoAssetIds = listOf(assetId),
            companionTagIds = listOf("tag_couple"),
            positivePointTagIds = listOf("tag_kind"),
            rating = 5,
            content = "맛있어요",
        )
    }

    @Test
    fun `가게 선택만으로 작성 완료하면 저장만 생긴다 (C1·C5)`() {
        val result = service.create(command())

        assertNull(result.reviewId)
        assertEquals(0, result.grantedCount)
        assertEquals(1, saveCommandPort.saves.size)
        assertTrue(saveCommandPort.reviews.isEmpty())
    }

    @Test
    fun `미충족 요청은 매장 집계를 건드리지 않는다 (P9·E6)`() {
        service.create(command(rating = 4, content = "본문만 있고 사진이 없다"))

        assertTrue(placeStatsPort.added.isEmpty())
    }

    @Test
    fun `본문이 공백뿐이면 리뷰가 되지 않는다 (C4 — 공백 제외 1자 이상)`() {
        val assetId = mediaAssetPort.seed(ownerId = 1)

        val result =
            service.create(
                command(
                    photoAssetIds = listOf(assetId),
                    companionTagIds = listOf("tag_couple"),
                    positivePointTagIds = listOf("tag_kind"),
                    rating = 5,
                    content = "   ",
                ),
            )

        assertNull(result.reviewId)
        assertTrue(placeStatsPort.added.isEmpty())
    }

    @Test
    fun `전 단계를 채우면 리뷰·티켓·집계가 함께 나간다 (C4·T6·TX-1)`() {
        val result = service.create(completeCommand())

        assertEquals(1, saveCommandPort.reviews.size)
        assertEquals(result.reviewId, saveCommandPort.reviews.single())
        assertEquals(1, result.grantedCount)
        assertEquals(1, result.availableCount)
        assertEquals(listOf(1L to 5), placeStatsPort.added)
    }

    @Test
    fun `리뷰가 확정되면 요약 트리거 이벤트를 발행한다 (TMT-232)`() {
        val result = service.create(completeCommand())

        assertEquals(listOf(ReviewCommittedEvent(reviewId = result.reviewId!!, placeId = 1)), published)
    }

    @Test
    fun `미충족이면 요약 트리거도 없다 (A1)`() {
        service.create(command())

        assertTrue(published.isEmpty())
    }

    @Test
    fun `보유 999장이면 리뷰는 되지만 티켓이 더 나가지 않는다 (T6)`() {
        ticketPort.seed(userId = 1, count = SaveRules.TICKET_MAX_AVAILABLE)

        val result = service.create(completeCommand())

        assertEquals(0, result.grantedCount)
        assertEquals(SaveRules.TICKET_MAX_AVAILABLE, result.availableCount)
        assertEquals(1, saveCommandPort.reviews.size)
    }

    @Test
    fun `사진은 STAGED에서 ATTACHED로 넘어간다 (M2)`() {
        val assetId = mediaAssetPort.seed(ownerId = 1)

        service.create(command(photoAssetIds = listOf(assetId)))

        assertTrue(mediaAssetPort.isAttached(assetId))
        assertEquals(listOf(assetId), saveCommandPort.photos.values.single())
    }

    @Test
    fun `남의 asset이면 MEDIA_NOT_OWNED고 저장이 생기지 않는다`() {
        val assetId = mediaAssetPort.seed(ownerId = 99)

        val ex = assertThrows<TmtException> { service.create(command(photoAssetIds = listOf(assetId))) }

        assertEquals(ErrorCode.MEDIA_NOT_OWNED, ex.errorCode)
        assertTrue(saveCommandPort.saves.isEmpty())
    }

    @Test
    fun `이미 붙은 asset이면 MEDIA_ALREADY_ATTACHED다`() {
        val assetId = mediaAssetPort.seed(ownerId = 1, attached = true)

        val ex = assertThrows<TmtException> { service.create(command(photoAssetIds = listOf(assetId))) }

        assertEquals(ErrorCode.MEDIA_ALREADY_ATTACHED, ex.errorCode)
    }

    @Test
    fun `없는 매장이면 PLACE_NOT_FOUND다`() {
        val ex = assertThrows<TmtException> { service.create(command(placeId = 404)) }

        assertEquals(ErrorCode.PLACE_NOT_FOUND, ex.errorCode)
    }

    @Test
    fun `정의에 없는 태그는 REVIEW_TAG_NOT_FOUND다`() {
        val ex = assertThrows<TmtException> { service.create(command(companionTagIds = listOf("tag_ghost"))) }

        assertEquals(ErrorCode.REVIEW_TAG_NOT_FOUND, ex.errorCode)
    }

    @Test
    fun `분류가 어긋난 태그도 REVIEW_TAG_NOT_FOUND다`() {
        val ex = assertThrows<TmtException> { service.create(command(positivePointTagIds = listOf("tag_couple"))) }

        assertEquals(ErrorCode.REVIEW_TAG_NOT_FOUND, ex.errorCode)
    }

    @Test
    fun `비활성 태그는 없는 태그와 같다`() {
        reviewTagPort.deactivate("tag_kind")

        val ex = assertThrows<TmtException> { service.create(command(positivePointTagIds = listOf("tag_kind"))) }

        assertEquals(ErrorCode.REVIEW_TAG_NOT_FOUND, ex.errorCode)
    }

    @Test
    fun `사진 4장 이상은 VALIDATION_FAILED다 (M3)`() {
        val ids = (1..4).map { mediaAssetPort.seed(ownerId = 1) }

        val ex = assertThrows<TmtException> { service.create(command(photoAssetIds = ids)) }

        assertEquals(ErrorCode.VALIDATION_FAILED, ex.errorCode)
    }

    @Test
    fun `같은 사진을 두 번 실어 보내면 VALIDATION_FAILED다`() {
        val assetId = mediaAssetPort.seed(ownerId = 1)

        val ex = assertThrows<TmtException> { service.create(command(photoAssetIds = listOf(assetId, assetId))) }

        assertEquals(ErrorCode.VALIDATION_FAILED, ex.errorCode)
    }

    @Test
    fun `별점이 범위 밖이면 VALIDATION_FAILED다 (R4)`() {
        val ex = assertThrows<TmtException> { service.create(command(rating = 6)) }

        assertEquals(ErrorCode.VALIDATION_FAILED, ex.errorCode)
    }

    @Test
    fun `본문 500자 초과는 REVIEW_CONTENT_TOO_LONG이다`() {
        val ex = assertThrows<TmtException> { service.create(command(content = "가".repeat(501))) }

        assertEquals(ErrorCode.REVIEW_CONTENT_TOO_LONG, ex.errorCode)
    }

    @Test
    fun `동행·좋은 점 태그가 한 배열로 중복 없이 저장된다`() {
        service.create(command(companionTagIds = listOf("tag_couple"), positivePointTagIds = listOf("tag_kind")))

        assertEquals(listOf("tag_couple", "tag_kind"), saveCommandPort.tags.values.single())
    }
}
