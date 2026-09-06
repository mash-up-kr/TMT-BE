package com.tmt.application.domain.recommendation

import com.tmt.application.domain.media.MediaUrlResolver
import com.tmt.application.port.input.PlaceRecommendationCommand
import com.tmt.application.port.output.llm.PlaceChoiceRequest
import com.tmt.application.port.output.llm.PlaceRecommendationLlmPort
import com.tmt.application.port.output.persistence.CandidatePlaceRow
import com.tmt.application.port.output.persistence.RecommendationQueryPort
import com.tmt.application.port.output.persistence.RecommendedPlaceRow
import com.tmt.application.port.output.persistence.ReviewedPlaceRow
import com.tmt.application.port.output.persistence.SeedPlaceRow
import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlaceRecommendationServiceTest {
    private val queryPort = FakeRecommendationQueryPort()
    private val llmPort = FakePlaceRecommendationLlmPort()
    private val service = PlaceRecommendationService(queryPort, llmPort, MediaUrlResolver("https://cdn.example.com"))

    // --- 격자 (§5-1) ---

    @Test
    fun `격자는 limit만큼 자르고 더 있으면 hasNext가 true다`() {
        queryPort.reviewedPlaces = (1L..3L).map { row(it) }

        val slice = service.list(userId = 1L, after = null, limit = 2)

        assertEquals(listOf(1L, 2L), slice.items.map { it.placeId })
        assertTrue(slice.hasNext)
    }

    @Test
    fun `사진 없는 매장은 thumbnailUrl이 null이고 항목은 남는다`() {
        // 화면이 categoryId 아이콘을 그리는 자리라 칸이 사라지면 안 된다 (C4-1·R11)
        queryPort.reviewedPlaces = listOf(row(1L, thumbnailS3Key = null))

        val item = service.list(userId = 1L, after = null, limit = 20).items.single()

        assertNull(item.thumbnailUrl)
        assertEquals("cat_korean", item.categoryId)
    }

    @Test
    fun `s3 키는 조회 URL로 바뀐다`() {
        queryPort.reviewedPlaces = listOf(row(1L, thumbnailS3Key = "reviews/a.jpg"))

        val item = service.list(userId = 1L, after = null, limit = 20).items.single()

        assertEquals("https://cdn.example.com/reviews/a.jpg", item.thumbnailUrl)
    }

    // --- 요청 검증 (§5-2) ---

    @Test
    fun `고른 매장이 1개면 VALIDATION_FAILED다`() {
        // 화면의 버튼 비활성은 요청이 오지 않는다는 보장이 아니다
        val e = assertFailsWith<TmtException> { service.recommend(command(listOf(1L))) }

        assertEquals(ErrorCode.VALIDATION_FAILED, e.errorCode)
    }

    @Test
    fun `고른 매장이 6개면 VALIDATION_FAILED다`() {
        // 상한이 없으면 LLM 호출 비용이 요청자 마음대로 늘어난다
        val e = assertFailsWith<TmtException> { service.recommend(command((1L..6L).toList())) }

        assertEquals(ErrorCode.VALIDATION_FAILED, e.errorCode)
    }

    @Test
    fun `같은 매장을 두 번 고르면 VALIDATION_FAILED다`() {
        val e = assertFailsWith<TmtException> { service.recommend(command(listOf(1L, 1L))) }

        assertEquals(ErrorCode.VALIDATION_FAILED, e.errorCode)
    }

    @Test
    fun `중복 검사가 개수 검사보다 먼저다`() {
        // (1,1)은 distinct하면 1개라 개수 검사에도 걸린다. 그래도 사용자에게는 중복이 원인이다
        val e = assertFailsWith<TmtException> { service.recommend(command(listOf(1L, 1L))) }

        assertTrue(e.detailMessage.orEmpty().contains("두 번"), "실제 메시지: ${e.detailMessage}")
    }

    @Test
    fun `내가 리뷰하지 않은 매장이 섞이면 PLACE_NOT_FOUND다`() {
        // 없는 매장과 남의 매장을 가르지 않는다 — 가르면 매장의 존재 여부를 응답이 흘린다 (A4)
        queryPort.reviewedIds = setOf(1L)

        val e = assertFailsWith<TmtException> { service.recommend(command(listOf(1L, 2L))) }

        assertEquals(ErrorCode.PLACE_NOT_FOUND, e.errorCode)
    }

    // --- 추천 (§5-2) ---

    @Test
    fun `후보가 없으면 RECOMMENDATION_UNAVAILABLE이다`() {
        queryPort.reviewedIds = setOf(1L, 2L)
        queryPort.candidates = emptyList()

        val e = assertFailsWith<TmtException> { service.recommend(command(listOf(1L, 2L))) }

        assertEquals(ErrorCode.RECOMMENDATION_UNAVAILABLE, e.errorCode)
    }

    @Test
    fun `LLM이 실패하면 RECOMMENDATION_FAILED다`() {
        queryPort.reviewedIds = setOf(1L, 2L)
        queryPort.candidates = listOf(candidate(9L))
        llmPort.failure = IllegalStateException("모든 프로바이더 실패")

        val e = assertFailsWith<TmtException> { service.recommend(command(listOf(1L, 2L))) }

        assertEquals(ErrorCode.RECOMMENDATION_FAILED, e.errorCode)
    }

    @Test
    fun `LLM이 고른 매장을 카드로 내린다`() {
        queryPort.reviewedIds = setOf(1L, 2L)
        queryPort.candidates = listOf(candidate(9L), candidate(10L))
        queryPort.recommended =
            RecommendedPlaceRow(
                placeId = 9L,
                name = "델리스피자",
                roadAddress = "서울 마포구 도화동 200-14",
                categoryId = "cat_western",
                thumbnailS3Key = "reviews/b.jpg",
                summaryReviewId = 31L,
                summaryPros = "분위기가 좋아요",
                summaryCons = "웨이팅이 많아요",
            )
        llmPort.chosen = 9L

        val view = service.recommend(command(listOf(1L, 2L)))

        assertEquals(9L, view.placeId)
        assertEquals("양식", view.categoryName)
        assertEquals("https://cdn.example.com/reviews/b.jpg", view.thumbnailUrl)
        assertEquals(31L, view.summary?.reviewId)
        assertEquals("분위기가 좋아요", view.summary?.pros)
    }

    @Test
    fun `요약이 아직 없으면 summary가 통째로 null이다`() {
        // 매장에 리뷰가 없는 경우와 요약 전인 경우가 화면에서 같다 (A2)
        queryPort.reviewedIds = setOf(1L, 2L)
        queryPort.candidates = listOf(candidate(9L))
        queryPort.recommended = recommended(9L).copy(summaryReviewId = null, summaryPros = null, summaryCons = null)
        llmPort.chosen = 9L

        val view = service.recommend(command(listOf(1L, 2L)))

        assertNull(view.summary)
    }

    @Test
    fun `카테고리 매핑에 실패하면 categoryName이 null이다`() {
        queryPort.reviewedIds = setOf(1L, 2L)
        queryPort.candidates = listOf(candidate(9L))
        queryPort.recommended = recommended(9L).copy(categoryId = "cat_unknown")
        llmPort.chosen = 9L

        assertNull(service.recommend(command(listOf(1L, 2L))).categoryName)
    }

    @Test
    fun `LLM에는 후보와 씨앗 리뷰가 함께 넘어간다`() {
        // 취향 근거가 빠지면 모델이 인기순 이상을 하지 못한다
        queryPort.reviewedIds = setOf(1L, 2L)
        queryPort.candidates = listOf(candidate(9L))
        queryPort.seeds = listOf(SeedPlaceRow(1L, "한판승부", "cat_meat", 5, "고기가 두껍다"))
        queryPort.recommended = recommended(9L)
        llmPort.chosen = 9L

        service.recommend(command(listOf(1L, 2L)))

        val sent = requireNotNull(llmPort.lastRequest)
        assertEquals(listOf(9L), sent.candidates.map { it.placeId })
        assertEquals("고기가 두껍다", sent.seeds.single().content)
        assertEquals(5, sent.seeds.single().rating)
    }

    @Test
    fun `추천된 매장을 다시 읽지 못하면 RECOMMENDATION_FAILED다`() {
        queryPort.reviewedIds = setOf(1L, 2L)
        queryPort.candidates = listOf(candidate(9L))
        queryPort.recommended = null
        llmPort.chosen = 9L

        val e = assertFailsWith<TmtException> { service.recommend(command(listOf(1L, 2L))) }

        assertEquals(ErrorCode.RECOMMENDATION_FAILED, e.errorCode)
    }

    private fun command(placeIds: List<Long>) = PlaceRecommendationCommand(userId = 1L, seedPlaceIds = placeIds)

    private fun row(
        placeId: Long,
        thumbnailS3Key: String? = "reviews/$placeId.jpg",
    ) = ReviewedPlaceRow(
        placeId = placeId,
        name = "매장$placeId",
        categoryId = "cat_korean",
        thumbnailS3Key = thumbnailS3Key,
        latestReviewedAt = Instant.parse("2026-09-06T00:00:00Z").plusSeconds(placeId),
    )

    private fun candidate(placeId: Long) =
        CandidatePlaceRow(
            placeId = placeId,
            name = "후보$placeId",
            categoryId = "cat_western",
            regionName = "마포구 도화동",
            reviewCount = 3,
            averageRating = 4.5,
        )

    private fun recommended(placeId: Long) =
        RecommendedPlaceRow(
            placeId = placeId,
            name = "델리스피자",
            roadAddress = "서울 마포구 도화동 200-14",
            categoryId = "cat_western",
            thumbnailS3Key = null,
            summaryReviewId = 31L,
            summaryPros = "분위기가 좋아요",
            summaryCons = null,
        )

    private class FakeRecommendationQueryPort : RecommendationQueryPort {
        var reviewedPlaces: List<ReviewedPlaceRow> = emptyList()

        /** 씨앗 검증이 통과할 매장 — 기본은 전부 통과다 (검증 외의 테스트가 여기 걸리지 않게) */
        var reviewedIds: Set<Long>? = null
        var seeds: List<SeedPlaceRow> = emptyList()
        var candidates: List<CandidatePlaceRow> = emptyList()
        var recommended: RecommendedPlaceRow? = null

        override fun findReviewedPlaceRows(
            userId: Long,
            afterLatestReviewedAt: Instant?,
            afterPlaceId: Long?,
            limitPlusOne: Int,
        ): List<ReviewedPlaceRow> = reviewedPlaces.take(limitPlusOne)

        override fun findReviewedPlaceIdsAmong(
            userId: Long,
            placeIds: List<Long>,
        ): List<Long> = reviewedIds?.let { allowed -> placeIds.filter { it in allowed } } ?: placeIds

        override fun findSeedPlaces(
            userId: Long,
            placeIds: List<Long>,
        ): List<SeedPlaceRow> = seeds

        override fun findCandidatePlaces(
            userId: Long,
            seedPlaceIds: List<Long>,
            limit: Int,
        ): List<CandidatePlaceRow> = candidates

        override fun findRecommendedPlace(placeId: Long): RecommendedPlaceRow? = recommended
    }

    private class FakePlaceRecommendationLlmPort : PlaceRecommendationLlmPort {
        var chosen: Long = 0
        var failure: Throwable? = null
        var lastRequest: PlaceChoiceRequest? = null

        override fun choose(request: PlaceChoiceRequest): Long {
            lastRequest = request
            failure?.let { throw it }
            return chosen
        }
    }
}
