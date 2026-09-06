package com.tmt.application.domain.place

import com.tmt.application.domain.review.ReviewCardComposer
import com.tmt.application.port.input.PlaceSearchKey
import com.tmt.application.port.input.PlaceSearchRequest
import com.tmt.application.port.output.persistence.PlaceSearchCriteria
import com.tmt.application.port.output.persistence.PlaceSearchPort
import com.tmt.application.port.output.persistence.PlaceSearchRow
import com.tmt.application.port.output.persistence.PlaceSearchRows
import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException
import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlaceSearchServiceTest {
    private val searchPort = mockk<PlaceSearchPort>()
    private val composer = mockk<ReviewCardComposer>()
    private val service = PlaceSearchService(searchPort, composer)

    private fun request(
        query: String? = null,
        curationTagId: String? = null,
        latitude: Double? = null,
        longitude: Double? = null,
        nearbyOnly: Boolean = false,
        after: PlaceSearchKey? = null,
    ) = PlaceSearchRequest(
        viewerId = null,
        query = query,
        curationTagId = curationTagId,
        latitude = latitude,
        longitude = longitude,
        nearbyOnly = nearbyOnly,
        after = after,
        limit = 20,
    )

    private fun row(
        placeId: Long,
        ratingSum: Long = 0,
        reviewCount: Int = 0,
        distanceMeters: Int? = null,
        sortValue: Int = 0,
    ) = PlaceSearchRow(
        placeId = placeId,
        name = "델리스피자",
        roadAddress = "서울 마포구 도화동 200-14",
        regionName = "마포구 도화동",
        categoryId = "cat_western",
        ratingSum = ratingSum,
        reviewCount = reviewCount,
        distanceMeters = distanceMeters,
        favorite = false,
        sortValue = sortValue,
    )

    private fun stubSearch(
        rows: List<PlaceSearchRow>,
        hasNext: Boolean = false,
    ): CapturingSlot<PlaceSearchCriteria> {
        val captured = slot<PlaceSearchCriteria>()
        every { searchPort.search(capture(captured)) } returns PlaceSearchRows(rows, hasNext)
        every { searchPort.findLatestPhotoKeys(any()) } returns emptyMap()
        return captured
    }

    @Test
    fun `query와 curationTagId 둘 다 없으면 VALIDATION_FAILED다`() {
        val error = assertFailsWith<TmtException> { service.search(request()) }
        assertEquals(ErrorCode.VALIDATION_FAILED, error.errorCode)
    }

    @Test
    fun `공백만 있는 query는 없는 것과 같다`() {
        val error = assertFailsWith<TmtException> { service.search(request(query = "   ")) }
        assertEquals(ErrorCode.VALIDATION_FAILED, error.errorCode)
    }

    @Test
    fun `nearbyOnly는 좌표가 없으면 VALIDATION_FAILED다`() {
        val error = assertFailsWith<TmtException> { service.search(request(query = "피자", nearbyOnly = true)) }
        assertEquals(ErrorCode.VALIDATION_FAILED, error.errorCode)
    }

    @Test
    fun `좌표가 없으면 유사도순 - 반경 제한도 걸지 않는다`() {
        val criteria = stubSearch(listOf(row(1)))

        service.search(request(query = "피자"))

        assertEquals(false, criteria.captured.sortByDistance)
        assertNull(criteria.captured.radiusMeters)
    }

    @Test
    fun `좌표가 오면 거리순으로 정렬한다`() {
        val criteria = stubSearch(listOf(row(1, distanceMeters = 320)))

        service.search(request(query = "피자", latitude = 37.5, longitude = 127.0))

        assertTrue(criteria.captured.sortByDistance)
        assertNull(criteria.captured.radiusMeters)
    }

    @Test
    fun `nearbyOnly면 반경 1km로 제한한다`() {
        val criteria = stubSearch(emptyList())

        service.search(request(query = "피자", latitude = 37.5, longitude = 127.0, nearbyOnly = true))

        assertEquals(PlaceSearchService.NEARBY_RADIUS_METERS, criteria.captured.radiusMeters)
    }

    @Test
    fun `칩은 검색 조건 프리셋으로 풀린다`() {
        val criteria = stubSearch(emptyList())

        service.search(request(curationTagId = "curation_ganmaek"))

        assertEquals("cat_pub", criteria.captured.categoryId)
        assertNull(criteria.captured.query)
    }

    @Test
    fun `알 수 없는 칩은 오류가 아니라 빈 결과다`() {
        val result = service.search(request(curationTagId = "curation_unknown"))

        assertEquals(emptyList(), result.items)
        assertEquals(false, result.hasNext)
        assertNull(result.lastKey)
    }

    @Test
    fun `결과 0건은 오류가 아니라 빈 목록이다`() {
        stubSearch(emptyList())

        val result = service.search(request(query = "없는가게"))

        assertEquals(emptyList(), result.items)
        assertNull(result.lastKey)
    }

    @Test
    fun `평균 별점은 rating_sum 나누기 review_count 소수 첫째 자리다`() {
        stubSearch(listOf(row(1, ratingSum = 14, reviewCount = 3)))

        // 14/3 = 4.666... → 4.7
        assertEquals(
            4.7,
            service
                .search(request(query = "피자"))
                .items
                .single()
                .averageRating,
        )
    }

    @Test
    fun `리뷰 0건이면 평균 별점이 없다 - 0으로 나누지 않는다`() {
        stubSearch(listOf(row(1)))

        assertNull(
            service
                .search(request(query = "피자"))
                .items
                .single()
                .averageRating,
        )
    }

    @Test
    fun `다음 커서의 재료는 마지막 행의 정렬 키다`() {
        stubSearch(listOf(row(3, sortValue = 700), row(2, sortValue = 700)), hasNext = true)

        val result = service.search(request(query = "피자"))

        assertEquals(PlaceSearchKey(700, 2), result.lastKey)
        assertTrue(result.hasNext)
    }

    @Test
    fun `커서의 정렬 키는 그대로 쿼리 조건으로 넘어간다`() {
        val criteria = stubSearch(emptyList())

        service.search(request(query = "피자", after = PlaceSearchKey(700, 2)))

        assertEquals(700, criteria.captured.afterSortValue)
        assertEquals(2L, criteria.captured.afterPlaceId)
    }
}
