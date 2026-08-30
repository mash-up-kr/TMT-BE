package com.tmt.application.domain.home

import com.tmt.application.domain.review.ReviewCardComposer
import com.tmt.application.port.input.HomeFeedRequest
import com.tmt.application.port.output.persistence.GroupCardRow
import com.tmt.application.port.output.persistence.HomeFeedRows
import com.tmt.application.port.output.persistence.HomeQueryPort
import com.tmt.application.port.output.persistence.MyGroupRow
import com.tmt.application.port.output.persistence.PhotoRow
import com.tmt.application.port.output.persistence.ReviewCardLookupPort
import com.tmt.application.port.output.persistence.ReviewCardRow
import com.tmt.application.port.output.persistence.SummaryRow
import com.tmt.application.port.output.persistence.TagRow
import com.tmt.common.exception.TmtException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant

/**
 * 홈 유스케이스 — 가입 그룹 0개의 성립, 추천 조회에 넘기는 조건, 좌표 유무에 따른 정렬 분기.
 * 가입 그룹 제외 자체는 SQL 술어라 여기서는 "누구 기준으로 몇 개를 요청하는가"까지 지킨다.
 */
class HomeServiceTest {
    private val queryPort = FakeHomeQueryPort()
    private val service = HomeService(queryPort, composer())

    @Test
    fun `가입 그룹이 0개여도 홈이 성립하고 추천은 채워진다`() {
        queryPort.myGroups = emptyList()
        queryPort.recommended = listOf(groupCardRow(3), groupCardRow(4))

        val result = service.get(viewerId = 1)

        assertEquals("하아얀", result.nickname)
        assertTrue(result.myGroups.isEmpty())
        assertEquals(listOf(3L, 4L), result.recommendedGroups.map { it.groupId })
    }

    @Test
    fun `추천은 조회자 기준 상위 5개를 요청한다 — 가입 그룹 제외는 이 조회의 계약이다`() {
        service.get(viewerId = 42)

        assertEquals(42L to HomeService.RECOMMENDED_COUNT, queryPort.recommendedCall)
    }

    @Test
    fun `사용자가 없으면 USER_NOT_FOUND다`() {
        queryPort.nickname = null

        assertThrows<TmtException> { service.get(viewerId = 1) }
    }

    @Test
    fun `그룹 대표 이미지와 커버는 s3 키에 미디어 base-url을 붙여 내린다`() {
        queryPort.myGroups = listOf(MyGroupRow(1, "성수 커피 탐험대", "groups/1.jpg"))
        queryPort.recommended = listOf(groupCardRow(3).copy(coverS3Key = "reviews/9.jpg"))

        val result = service.get(viewerId = 1)

        assertEquals("https://cdn.example/groups/1.jpg", result.myGroups[0].imageUrl)
        assertEquals("https://cdn.example/reviews/9.jpg", result.recommendedGroups[0].coverImageUrl)
    }

    @Test
    fun `대표 이미지가 없으면 imageUrl은 null이다`() {
        queryPort.myGroups = listOf(MyGroupRow(1, "성수 커피 탐험대", null))

        assertNull(service.get(viewerId = 1).myGroups[0].imageUrl)
    }

    @Test
    fun `좌표가 있으면 거리순으로 읽고 커서 키에 거리가 실린다`() {
        queryPort.feedRows = HomeFeedRows(listOf(reviewCardRow(distanceMeters = 120)), hasNext = true)

        val result = service.get(HomeFeedRequest(viewerId = 1, latitude = 37.4, longitude = 127.0, limit = 20))

        assertTrue(result.sortedByDistance)
        assertEquals("distance", queryPort.lastFeedMode)
        assertEquals(120, result.lastKey?.distanceMeters)
        assertEquals(7L, result.lastKey?.reviewId)
    }

    @Test
    fun `좌표가 없으면 최신순으로 읽고 커서 키에 createdAt이 실린다`() {
        queryPort.feedRows = HomeFeedRows(listOf(reviewCardRow(distanceMeters = null)), hasNext = true)

        val result = service.get(HomeFeedRequest(viewerId = 1, latitude = null, longitude = null, limit = 20))

        assertFalse(result.sortedByDistance)
        assertEquals("recency", queryPort.lastFeedMode)
        assertEquals(CREATED_AT, result.lastKey?.createdAt)
        assertEquals(7L, result.lastKey?.reviewId)
        assertNull(result.lastKey?.distanceMeters)
    }

    @Test
    fun `가입한 그룹이 없으면 피드는 빈 목록이고 커서를 발급하지 않는다`() {
        queryPort.feedRows = HomeFeedRows(emptyList(), hasNext = false)

        val result = service.get(HomeFeedRequest(viewerId = 1, latitude = 37.4, longitude = 127.0, limit = 20))

        assertTrue(result.items.isEmpty())
        assertFalse(result.hasNext)
        assertNull(result.lastKey)
    }

    @Test
    fun `위경도 범위를 벗어나면 400이다`() {
        assertThrows<TmtException> {
            service.get(HomeFeedRequest(viewerId = 1, latitude = 91.0, longitude = 127.0, limit = 20))
        }
    }

    private fun composer() =
        ReviewCardComposer(
            object : ReviewCardLookupPort {
                override fun findPhotoRows(saveIds: Collection<Long>): List<PhotoRow> = emptyList()

                override fun findTagRows(saveIds: Collection<Long>): List<TagRow> = emptyList()

                override fun findSummaryRows(reviewIds: Collection<Long>): List<SummaryRow> = emptyList()
            },
            mediaBaseUrl = "https://cdn.example",
        )

    private fun groupCardRow(id: Long) =
        GroupCardRow(
            groupId = id,
            name = "성수 커피 탐험대",
            oneLineDescription = "커피 좋아하는 사람 모여라",
            coverS3Key = null,
            memberCount = 12,
            reviewCount = 30,
            placeCount = 11,
            matchedSavedPlaceCount = 7,
        )

    private fun reviewCardRow(distanceMeters: Int?) =
        ReviewCardRow(
            reviewId = 7,
            saveId = 70,
            createdAt = CREATED_AT,
            rating = 5,
            content = "맛있었어요",
            authorId = 901,
            authorNickname = "미식가",
            authorProfileImageUrl = null,
            placeId = 5,
            placeName = "큰집",
            placeRegionName = "구로구 구로동",
            placeCategoryId = "cat_meat",
            distanceMeters = distanceMeters,
            favorite = false,
        )

    private class FakeHomeQueryPort : HomeQueryPort {
        var nickname: String? = "하아얀"
        var myGroups: List<MyGroupRow> = emptyList()
        var recommended: List<GroupCardRow> = emptyList()
        var feedRows: HomeFeedRows = HomeFeedRows(emptyList(), hasNext = false)
        var recommendedCall: Pair<Long, Int>? = null
        var lastFeedMode: String? = null

        override fun findNickname(userId: Long): String? = nickname

        override fun findMyGroups(userId: Long): List<MyGroupRow> = myGroups

        override fun findRecommendedGroups(
            userId: Long,
            limit: Int,
        ): List<GroupCardRow> {
            recommendedCall = userId to limit
            return recommended
        }

        override fun findFeedRowsByDistance(
            userId: Long,
            latitude: Double,
            longitude: Double,
            afterDistanceMeters: Int?,
            afterReviewId: Long?,
            limit: Int,
        ): HomeFeedRows {
            lastFeedMode = "distance"
            return feedRows
        }

        override fun findFeedRowsByRecency(
            userId: Long,
            afterCreatedAt: Instant?,
            afterReviewId: Long?,
            limit: Int,
        ): HomeFeedRows {
            lastFeedMode = "recency"
            return feedRows
        }
    }

    private companion object {
        val CREATED_AT: Instant = Instant.parse("2026-08-20T00:00:00Z")
    }
}
