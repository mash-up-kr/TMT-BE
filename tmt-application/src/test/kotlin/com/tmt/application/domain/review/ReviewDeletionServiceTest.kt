package com.tmt.application.domain.review

import com.tmt.application.domain.media.FakeMediaAssetPort
import com.tmt.application.domain.save.FakeGroupJoinTicketPort
import com.tmt.application.domain.save.FakePlaceStatsPort
import com.tmt.application.port.output.persistence.ReviewDeletionRow
import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TicketShortageException
import com.tmt.common.exception.TmtException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.context.ApplicationEvent
import org.springframework.context.ApplicationEventPublisher

/**
 * 리뷰 삭제 (I §6-4). 되돌리는 항목이 많아 "무엇이 어떤 순서로 되돌아가는가"가 이 테스트의 대상이다.
 */
class ReviewDeletionServiceTest {
    private val ownerId = 1L
    private val reviewId = 1L
    private val saveId = 10L
    private val placeId = 9L

    private val deletionRow =
        ReviewDeletionRow(reviewId = reviewId, saveId = saveId, userId = ownerId, placeId = placeId, rating = 4)

    private val tickets = FakeGroupJoinTicketPort()
    private val placeStats = FakePlaceStatsPort()
    private val groupStats = FakeGroupStatsPort()
    private val media = FakeMediaAssetPort()
    private val events = mutableListOf<Any>()

    private val assetId = media.seed(ownerId = ownerId, attached = true)

    private val commandPort = FakeReviewCommandPort(assetIdsBySave = mapOf(saveId to listOf(assetId)))
    private val sharePort = FakeGroupReviewSharePort(groupIdsByReview = mapOf(reviewId to listOf(3L, 4L)))

    private val service =
        ReviewDeletionService(
            reviewQueryPort = FakeReviewQueryPort(deletions = mapOf(reviewId to deletionRow)),
            reviewCommandPort = commandPort,
            groupJoinTicketPort = tickets,
            groupReviewSharePort = sharePort,
            groupStatsPort = groupStats,
            placeStatsPort = placeStats,
            mediaAssetPort = media,
            eventPublisher = collectingPublisher(),
        )

    @Test
    fun `삭제하면 티켓 1장을 회수하고 매장 집계를 되돌린다 (R6·R7)`() {
        tickets.seed(ownerId, 1)

        service.delete(ownerId, reviewId)

        assertEquals(0, tickets.countAvailable(ownerId))
        assertEquals(listOf(placeId to 4), placeStats.removed)
        assertEquals(listOf(reviewId), commandPort.softDeletedReviews)
        assertEquals(listOf(saveId), commandPort.softDeletedSaves)
    }

    @Test
    fun `사진은 저장으로 되돌아가지 않고 완전히 사라진다 (R6)`() {
        tickets.seed(ownerId, 1)

        service.delete(ownerId, reviewId)

        assertEquals(listOf(saveId), commandPort.deletedPhotoSaveIds)
        assertFalse(media.exists(assetId))
        // S3 객체 삭제는 커밋 후 이벤트로 나간다 — 트랜잭션 안에서 외부 I/O를 하지 않는다
        val event = events.filterIsInstance<ReviewPhotosDeletedEvent>().single()
        assertEquals(listOf("review/$assetId.jpg"), event.s3Keys)
    }

    @Test
    fun `공유된 그룹에서 리뷰가 내려가고 그룹 지표가 다시 맞춰진다`() {
        tickets.seed(ownerId, 1)

        service.delete(ownerId, reviewId)

        assertEquals(listOf(reviewId), sharePort.unsharedReviewIds)
        assertEquals(listOf(3L, 4L), groupStats.refreshed)
    }

    @Test
    fun `회수할 티켓이 0장이면 삭제를 거부하고 아무것도 되돌리지 않는다`() {
        val e = assertThrows<TicketShortageException> { service.delete(ownerId, reviewId) }

        assertEquals(ErrorCode.REVIEW_DELETE_TICKET_REQUIRED, e.errorCode)
        assertEquals(1, e.requiredCount)
        assertEquals(0, e.availableCount)
        assertEquals(1, e.shortageCount)
        assertTrue(placeStats.removed.isEmpty())
        assertTrue(commandPort.softDeletedReviews.isEmpty())
        assertTrue(media.exists(assetId))
    }

    @Test
    fun `이미 지워진 리뷰를 다시 지우면 REVIEW_NOT_FOUND다`() {
        // 티켓 2장 — 이 리뷰가 발급한 장이 이미 회수됐어도 회수는 다른 장으로 성공한다.
        // 그래서 갱신 행 수를 안 보면 두 번째 삭제가 끝까지 진행돼 집계가 두 번 깎인다
        tickets.seed(ownerId, 2)
        service.delete(ownerId, reviewId)

        val e = assertThrows<TmtException> { service.delete(ownerId, reviewId) }

        assertEquals(ErrorCode.REVIEW_NOT_FOUND, e.errorCode)
        // 되돌림이 실제로 무효가 되는 것은 트랜잭션 롤백이라 여기서는 확인할 수 없다 —
        // Fake는 롤백이 없어 호출이 그대로 남는다. 경합·롤백 검증은 TMT-295 통합 테스트로 간다
    }

    @Test
    fun `타인의 리뷰와 없는 리뷰는 둘 다 REVIEW_NOT_FOUND다`() {
        tickets.seed(ownerId, 1)
        tickets.seed(2L, 1)

        val others = assertThrows<TmtException> { service.delete(2L, reviewId) }
        val missing = assertThrows<TmtException> { service.delete(ownerId, 999L) }

        assertEquals(ErrorCode.REVIEW_NOT_FOUND, others.errorCode)
        assertEquals(ErrorCode.REVIEW_NOT_FOUND, missing.errorCode)
        // 남의 리뷰를 지우려다 내 티켓이 줄어들면 안 된다
        assertEquals(1, tickets.countAvailable(2L))
    }

    private fun collectingPublisher() =
        object : ApplicationEventPublisher {
            override fun publishEvent(event: ApplicationEvent) {
                events += event
            }

            override fun publishEvent(event: Any) {
                events += event
            }
        }
}
