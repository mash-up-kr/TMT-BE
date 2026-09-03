package com.tmt.application.domain.save

import com.tmt.application.domain.aisummary.ReviewCommittedEvent
import com.tmt.application.domain.media.FakeMediaAssetPort
import com.tmt.application.domain.media.MediaAttachmentService
import com.tmt.application.port.input.CreateSaveCommand
import com.tmt.application.port.input.PlaceSelection
import com.tmt.application.port.input.UpdateSaveCommand
import com.tmt.application.port.output.persistence.SaveCommandPort
import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.context.ApplicationEventPublisher

class SaveUpdateServiceTest {
    private val saveCommandPort = FakeSaveCommandPort()
    private val saveQueryPort = FakeSaveQueryPort(saveCommandPort)
    private val placeQueryPort = FakePlaceQueryPort(existingPlaceIds = setOf(1L, 2L))
    private val reviewTagPort = FakeReviewTagPort()
    private val mediaAssetPort = FakeMediaAssetPort()
    private val ticketPort = FakeGroupJoinTicketPort()
    private val placeStatsPort = FakePlaceStatsPort()
    private val published = mutableListOf<Any>()
    private val attachMediaUseCase = MediaAttachmentService(mediaAssetPort, baseUrl = "https://media.tmt.example")
    private val writeSupport =
        SaveWriteSupport(
            reviewTagPort = reviewTagPort,
            attachMediaUseCase = attachMediaUseCase,
            groupJoinTicketPort = ticketPort,
        )

    private val creationService =
        SaveCreationService(
            saveCommandPort = saveCommandPort,
            placeQueryPort = placeQueryPort,
            placeCommandPort = FakePlaceCommandPort(),
            saveWriteSupport = writeSupport,
            attachMediaUseCase = attachMediaUseCase,
            placeStatsPort = placeStatsPort,
            eventPublisher = ApplicationEventPublisher { published += it },
        )

    private val service =
        SaveUpdateService(
            saveQueryPort = saveQueryPort,
            saveCommandPort = saveCommandPort,
            saveWriteSupport = writeSupport,
            attachMediaUseCase = attachMediaUseCase,
            placeStatsPort = placeStatsPort,
            eventPublisher = ApplicationEventPublisher { published += it },
        )

    /** 이어쓰기의 출발점은 항상 실제로 만들어진 저장이다. */
    private fun seedDraft(
        userId: Long = 1,
        placeId: Long = 1,
        rating: Int? = null,
        photoAssetIds: List<Long> = emptyList(),
    ): Long =
        creationService
            .create(
                CreateSaveCommand(
                    userId = userId,
                    place = PlaceSelection.Existing(placeId),
                    photoAssetIds = photoAssetIds,
                    companionTagIds = emptyList(),
                    positivePointTagIds = emptyList(),
                    rating = rating,
                    content = null,
                ),
            ).saveId

    private fun updateCommand(
        saveId: Long,
        userId: Long = 1,
        placeId: Long? = 1,
        newPlaceRequested: Boolean = false,
        photoAssetIds: List<Long> = emptyList(),
        companionTagIds: List<String> = emptyList(),
        positivePointTagIds: List<String> = emptyList(),
        rating: Int? = null,
        content: String? = null,
    ) = UpdateSaveCommand(
        userId = userId,
        saveId = saveId,
        placeId = placeId,
        newPlaceRequested = newPlaceRequested,
        photoAssetIds = photoAssetIds,
        companionTagIds = companionTagIds,
        positivePointTagIds = positivePointTagIds,
        rating = rating,
        content = content,
    )

    @Test
    fun `이어쓰기로 판정이 충족되면 그 시점에 리뷰·티켓·집계가 나간다 (C6)`() {
        val saveId = seedDraft()
        val assetId = mediaAssetPort.seed(ownerId = 1)

        val result =
            service.update(
                updateCommand(
                    saveId = saveId,
                    photoAssetIds = listOf(assetId),
                    companionTagIds = listOf("tag_couple"),
                    positivePointTagIds = listOf("tag_kind"),
                    rating = 5,
                    content = "맛있어요",
                ),
            )

        assertNotNull(result.reviewId)
        assertEquals(1, result.grantedCount)
        assertEquals(listOf(1L to 5), placeStatsPort.added)
        assertTrue(published.any { it is ReviewCommittedEvent })
    }

    @Test
    fun `사진 없이 이어써도 리뷰·티켓이 나간다 (C4-1)`() {
        val saveId = seedDraft()

        val result =
            service.update(
                updateCommand(
                    saveId = saveId,
                    companionTagIds = listOf("tag_couple"),
                    positivePointTagIds = listOf("tag_kind"),
                    rating = 5,
                    content = "사진은 못 찍었지만 맛있었다",
                ),
            )

        assertNotNull(result.reviewId)
        assertEquals(1, result.grantedCount)
        assertEquals(listOf(1L to 5), placeStatsPort.added)
        assertTrue(published.any { it is ReviewCommittedEvent })
    }

    @Test
    fun `판정을 못 채우면 리뷰도 티켓도 집계도 없다 (C4)`() {
        val saveId = seedDraft()

        val result = service.update(updateCommand(saveId = saveId, rating = 4, content = "아직 사진이 없다"))

        assertNull(result.reviewId)
        assertEquals(0, result.grantedCount)
        assertTrue(placeStatsPort.added.isEmpty())
    }

    @Test
    fun `이미 리뷰가 된 저장은 이어쓸 수 없다 (S4)`() {
        val assetId = mediaAssetPort.seed(ownerId = 1)
        val saveId =
            creationService
                .create(
                    CreateSaveCommand(
                        userId = 1,
                        place = PlaceSelection.Existing(1),
                        photoAssetIds = listOf(assetId),
                        companionTagIds = listOf("tag_couple"),
                        positivePointTagIds = listOf("tag_kind"),
                        rating = 5,
                        content = "맛있어요",
                    ),
                ).saveId

        val error = assertThrows<TmtException> { service.update(updateCommand(saveId = saveId, rating = 3)) }

        assertEquals(ErrorCode.SAVE_ALREADY_REVIEWED, error.errorCode)
    }

    @Test
    fun `매장을 바꾸려 하면 SAVE_PLACE_IMMUTABLE이다 (S6)`() {
        val saveId = seedDraft(placeId = 1)

        assertEquals(
            ErrorCode.SAVE_PLACE_IMMUTABLE,
            assertThrows<TmtException> { service.update(updateCommand(saveId = saveId, placeId = 2)) }.errorCode,
        )
        // 읽을 수 없는 placeId·매장 직접 등록도 같은 취급이다
        assertEquals(
            ErrorCode.SAVE_PLACE_IMMUTABLE,
            assertThrows<TmtException> { service.update(updateCommand(saveId = saveId, placeId = null)) }.errorCode,
        )
        assertEquals(
            ErrorCode.SAVE_PLACE_IMMUTABLE,
            assertThrows<TmtException> {
                service.update(updateCommand(saveId = saveId, newPlaceRequested = true))
            }.errorCode,
        )
    }

    @Test
    fun `남의 저장은 없는 저장과 같게 404다 (S8)`() {
        val saveId = seedDraft(userId = 1)

        assertEquals(
            ErrorCode.SAVE_NOT_FOUND,
            assertThrows<TmtException> { service.update(updateCommand(saveId = saveId, userId = 2)) }.errorCode,
        )
        assertEquals(
            ErrorCode.SAVE_NOT_FOUND,
            assertThrows<TmtException> { service.delete(userId = 2, saveId = saveId) }.errorCode,
        )
    }

    @Test
    fun `이어쓰기에서 유지한 사진은 다시 붙일 수 있고 빠진 사진은 STAGED로 돌아간다 (M2·M4)`() {
        val kept = mediaAssetPort.seed(ownerId = 1)
        val dropped = mediaAssetPort.seed(ownerId = 1)
        val saveId = seedDraft(photoAssetIds = listOf(kept, dropped))

        service.update(updateCommand(saveId = saveId, photoAssetIds = listOf(kept)))

        assertEquals(listOf(kept), saveCommandPort.photos[saveId])
        assertTrue(mediaAssetPort.isAttached(kept))
        assertTrue(!mediaAssetPort.isAttached(dropped))
    }

    @Test
    fun `임시저장을 버리면 행이 사라지고 사진은 STAGED로 돌아간다 (F·G·I §5-2·M4)`() {
        val assetId = mediaAssetPort.seed(ownerId = 1)
        val saveId = seedDraft(photoAssetIds = listOf(assetId))

        service.delete(userId = 1, saveId = saveId)

        assertNull(saveQueryPort.findSave(saveId))
        // 사진은 지우지 않는다 — 미부착 TTL이 정리한다
        assertTrue(mediaAssetPort.exists(assetId))
        assertTrue(!mediaAssetPort.isAttached(assetId))
    }

    @Test
    fun `이미 지운 저장을 다시 지워도 404다 (F·G·I §5-2)`() {
        val saveId = seedDraft()
        service.delete(userId = 1, saveId = saveId)

        assertEquals(
            ErrorCode.SAVE_NOT_FOUND,
            assertThrows<TmtException> { service.delete(userId = 1, saveId = saveId) }.errorCode,
        )
    }

    @Test
    fun `리뷰가 된 저장을 지우려 하면 SAVE_ALREADY_REVIEWED다 (R6·R7)`() {
        val assetId = mediaAssetPort.seed(ownerId = 1)
        val saveId =
            creationService
                .create(
                    CreateSaveCommand(
                        userId = 1,
                        place = PlaceSelection.Existing(1),
                        photoAssetIds = listOf(assetId),
                        companionTagIds = listOf("tag_couple"),
                        positivePointTagIds = listOf("tag_kind"),
                        rating = 5,
                        content = "맛있어요",
                    ),
                ).saveId

        val error = assertThrows<TmtException> { service.delete(userId = 1, saveId = saveId) }

        assertEquals(ErrorCode.SAVE_ALREADY_REVIEWED, error.errorCode)
    }

    @Test
    fun `없는 저장은 SAVE_NOT_FOUND다`() {
        assertEquals(
            ErrorCode.SAVE_NOT_FOUND,
            assertThrows<TmtException> { service.delete(userId = 1, saveId = 999) }.errorCode,
        )
    }

    @Test
    fun `조회 뒤 동시 삭제로 갱신이 0행이면 404다 (TMT-301)`() {
        val saveId = seedDraft()

        // 조회는 성공하고 UPDATE만 0행인 상태 — 실제 동시 DELETE가 만드는 창이다.
        // 그대로 진행하면 사라진 save_id로 insertPhotos가 FK를 위반해 500이 된다
        val error = assertThrows<TmtException> { raceLostService.update(updateCommand(saveId)) }

        assertEquals(ErrorCode.SAVE_NOT_FOUND, error.errorCode)
    }

    @Test
    fun `조회 뒤 동시 삭제로 삭제가 0행이면 404다 (TMT-301)`() {
        val saveId = seedDraft()

        val error = assertThrows<TmtException> { raceLostService.delete(userId = 1, saveId = saveId) }

        assertEquals(ErrorCode.SAVE_NOT_FOUND, error.errorCode)
    }

    /** 조회는 통과시키고 쓰기만 0행으로 돌려 경합에 밀린 순간을 만든다. */
    private val raceLostService =
        SaveUpdateService(
            saveQueryPort = saveQueryPort,
            saveCommandPort = RaceLostCommandPort(saveCommandPort),
            saveWriteSupport = writeSupport,
            attachMediaUseCase = attachMediaUseCase,
            placeStatsPort = placeStatsPort,
            eventPublisher = ApplicationEventPublisher { published += it },
        )

    private class RaceLostCommandPort(
        delegate: SaveCommandPort,
    ) : SaveCommandPort by delegate {
        override fun updateSave(
            saveId: Long,
            rating: Int?,
            content: String?,
        ): Int = 0

        override fun deleteSave(saveId: Long): Int = 0
    }
}
