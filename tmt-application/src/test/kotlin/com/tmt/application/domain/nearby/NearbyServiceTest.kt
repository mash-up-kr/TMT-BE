package com.tmt.application.domain.nearby

import com.tmt.application.domain.media.MediaUrlResolver
import com.tmt.application.domain.review.ReviewCardComposer
import com.tmt.application.port.input.NearbyPlacesRequest
import com.tmt.application.port.input.NearbyReviewsRequest
import com.tmt.application.port.output.persistence.NearbyQueryPort
import com.tmt.application.port.output.persistence.NearbyReviewRows
import com.tmt.application.port.output.persistence.PinRow
import com.tmt.application.port.output.persistence.ReviewCardLookupPort
import com.tmt.application.port.output.persistence.ReviewCardRow
import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NearbyServiceTest {
    private val port = mockk<NearbyQueryPort>()
    private val lookup =
        mockk<ReviewCardLookupPort> {
            every { findPhotoRows(any()) } returns emptyList()
            every { findTagRows(any()) } returns emptyList()
            every { findSummaryRows(any()) } returns emptyList()
        }
    private val service = NearbyService(port, ReviewCardComposer(lookup, MediaUrlResolver("https://media.example/")))

    private fun row(
        reviewId: Long,
        saveId: Long = reviewId,
        distance: Int = 100,
    ) = ReviewCardRow(
        reviewId = reviewId,
        saveId = saveId,
        createdAt = Instant.EPOCH,
        rating = 5,
        content = "본문",
        authorId = 1,
        authorNickname = "먹짱",
        authorProfileImageUrl = null,
        placeId = 10,
        placeName = "가게",
        placeRegionName = "마포구 서교동",
        placeCategoryId = "cat_korean",
        distanceMeters = distance,
        favorite = true,
    )

    private fun pin(id: Long) = PinRow(id, "p$id", 37.5, 126.9, "cat_korean", 1)

    @Test
    fun `위경도 범위 밖이면 VALIDATION_FAILED다`() {
        val error =
            assertFailsWith<TmtException> {
                service.get(NearbyReviewsRequest(viewerId = null, latitude = 91.0, longitude = 126.92, limit = 20))
            }
        assertEquals(ErrorCode.VALIDATION_FAILED, error.errorCode)
    }

    @Test
    fun `viewport가 뒤집혀 있으면 VALIDATION_FAILED다`() {
        val error =
            assertFailsWith<TmtException> {
                service.get(NearbyPlacesRequest(north = 37.5, south = 37.6, east = 127.0, west = 126.9))
            }
        assertEquals(ErrorCode.VALIDATION_FAILED, error.errorCode)
    }

    @Test
    fun `핀이 상한을 넘으면 30개로 자르고 truncated다`() {
        every {
            port.findPins(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns (1L..31L).map { pin(it) }

        val result = service.get(NearbyPlacesRequest(north = 37.6, south = 37.5, east = 127.0, west = 126.9))

        assertEquals(30, result.pins.size)
        assertTrue(result.truncated)
    }

    @Test
    fun `알 수 없는 큐레이션 칩은 쿼리 없이 빈 결과다`() {
        val result =
            service.get(
                NearbyPlacesRequest(
                    north = 37.6,
                    south = 37.5,
                    east = 127.0,
                    west = 126.9,
                    curationTagId = "curation_nope",
                ),
            )

        assertEquals(emptyList(), result.pins)
        verify(
            exactly = 0,
        ) { port.findPins(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `검색어가 카테고리 라벨에 걸리면 해당 카테고리 id를 함께 넘긴다`() {
        val captured = mutableListOf<List<String>>()
        every {
            port.findPins(any(), any(), any(), any(), any(), any(), any(), capture(captured), any(), any(), any())
        } returns emptyList()

        service.get(NearbyPlacesRequest(north = 37.6, south = 37.5, east = 127.0, west = 126.9, query = "카페"))

        assertEquals(listOf("cat_cafe"), captured.single())
    }

    @Test
    fun `마지막 페이지면 lastKey 기반 커서 재료가 그대로 남고 hasNext만 false다`() {
        every { port.findReviewRowsWithin(any(), any(), any(), any(), any(), any(), any()) } returns
            NearbyReviewRows(listOf(row(1, distance = 5)), hasNext = false)

        val result =
            service.get(
                NearbyReviewsRequest(viewerId = null, latitude = 37.55, longitude = 126.92, limit = 20),
            )

        assertEquals(false, result.hasNext)
        assertEquals(5, result.lastKey?.distanceMeters)
    }

    @Test
    fun `반경 안에 리뷰가 없으면 빈 페이지다`() {
        every { port.findReviewRowsWithin(any(), any(), any(), any(), any(), any(), any()) } returns
            NearbyReviewRows(emptyList(), hasNext = false)

        val result =
            service.get(
                NearbyReviewsRequest(viewerId = null, latitude = 37.55, longitude = 126.92, limit = 20),
            )

        assertEquals(emptyList(), result.items)
        assertNull(result.lastKey)
    }
}
