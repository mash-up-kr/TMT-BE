package com.tmt.application.domain.place

import com.tmt.application.domain.review.ReviewCardComposer
import com.tmt.application.port.output.persistence.PlaceDetailRow
import com.tmt.application.port.output.persistence.PlaceFavoritePort
import com.tmt.application.port.output.persistence.PlacePhotoRow
import com.tmt.application.port.output.persistence.PlaceQueryPort
import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class PlaceDetailServiceTest {
    private val queryPort = mockk<PlaceQueryPort>()
    private val favoritePort = mockk<PlaceFavoritePort>(relaxed = true)
    private val composer = mockk<ReviewCardComposer>()
    private val service = PlaceDetailService(queryPort, favoritePort, composer)

    private fun detailRow(
        ratingSum: Long,
        reviewCount: Int,
    ) = PlaceDetailRow(
        placeId = 1,
        name = "우래옥",
        categoryId = "cat_korean",
        ratingSum = ratingSum,
        reviewCount = reviewCount,
        roadAddress = "서울 중구",
        latitude = 37.5,
        longitude = 127.0,
        phoneNumber = null,
        favorite = false,
    )

    @Test
    fun `평균 별점은 rating_sum 나누기 review_count 소수 첫째 자리다`() {
        every { queryPort.findPlaceDetail(1, null) } returns detailRow(ratingSum = 14, reviewCount = 3)
        every { queryPort.findRecentPlacePhotos(1, any()) } returns emptyList()

        // 14/3 = 4.666... → 4.7
        assertEquals(4.7, service.get(null, 1).averageRating)
    }

    @Test
    fun `리뷰 0건이면 평균 별점 없이 성립한다 - 0으로 나누지 않는다`() {
        every { queryPort.findPlaceDetail(1, null) } returns detailRow(ratingSum = 0, reviewCount = 0)
        every { queryPort.findRecentPlacePhotos(1, any()) } returns emptyList()

        val detail = service.get(null, 1)

        assertNull(detail.averageRating)
        assertEquals(0, detail.reviewCount)
    }

    @Test
    fun `카테고리 라벨과 대표 사진 URL을 조립한다`() {
        every { queryPort.findPlaceDetail(1, 9) } returns detailRow(ratingSum = 5, reviewCount = 1)
        every { queryPort.findRecentPlacePhotos(1, any()) } returns listOf(PlacePhotoRow("review/a.jpg", 3))
        every { composer.mediaUrl("review/a.jpg") } returns "https://media.example/review/a.jpg"

        val detail = service.get(9, 1)

        assertEquals("한식", detail.categoryName)
        assertEquals("https://media.example/review/a.jpg", detail.photos.single().url)
        assertEquals(3, detail.photos.single().reviewId)
    }

    @Test
    fun `없는 매장은 PLACE_NOT_FOUND다`() {
        every { queryPort.findPlaceDetail(99, null) } returns null

        val error = assertFailsWith<TmtException> { service.get(null, 99) }

        assertEquals(ErrorCode.PLACE_NOT_FOUND, error.errorCode)
    }

    @Test
    fun `찜 추가·해제는 매장 존재를 확인한 뒤 포트에 위임한다`() {
        every { queryPort.existsPlace(1) } returns true

        service.add(7, 1)
        service.remove(7, 1)

        verify { favoritePort.add(7, 1) }
        verify { favoritePort.remove(7, 1) }
    }

    @Test
    fun `없는 매장 찜은 PLACE_NOT_FOUND다`() {
        every { queryPort.existsPlace(99) } returns false

        assertFailsWith<TmtException> { service.add(7, 99) }
        verify(exactly = 0) { favoritePort.add(any(), any()) }
    }
}
