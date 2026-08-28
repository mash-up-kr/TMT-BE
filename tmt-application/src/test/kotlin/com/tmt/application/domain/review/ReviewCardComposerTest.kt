package com.tmt.application.domain.review

import com.tmt.application.port.output.persistence.PhotoRow
import com.tmt.application.port.output.persistence.ReviewCardLookupPort
import com.tmt.application.port.output.persistence.ReviewCardRow
import com.tmt.application.port.output.persistence.SummaryRow
import com.tmt.application.port.output.persistence.TagRow
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReviewCardComposerTest {
    private val lookup = mockk<ReviewCardLookupPort>()
    private val composer = ReviewCardComposer(lookup, mediaBaseUrl = "https://media.example/")

    private fun row(
        reviewId: Long,
        distance: Int? = 100,
    ) = ReviewCardRow(
        reviewId = reviewId,
        saveId = reviewId,
        createdAt = Instant.EPOCH,
        rating = 5,
        content = "본문",
        authorId = 1,
        authorNickname = "먹짱",
        authorProfileImageUrl = null,
        placeId = 10,
        placeName = "가게",
        placeRegionName = "마포구 서교동",
        distanceMeters = distance,
        favorite = true,
    )

    @Test
    fun `사진 URL은 base-url과 s3_key로 조립하고 태그·요약·찜을 붙인다`() {
        every { lookup.findPhotoRows(listOf(1L)) } returns
            listOf(PhotoRow(saveId = 1, savePhotoId = 7, s3Key = "review/a.jpg", photoOrder = 0))
        every { lookup.findTagRows(listOf(1L)) } returns listOf(TagRow(1, "tag_alone", "혼자"))
        every { lookup.findSummaryRows(listOf(1L)) } returns listOf(SummaryRow(1, "좋아요", null))

        val item = composer.compose(listOf(row(1))).single()

        assertEquals("https://media.example/review/a.jpg", item.photos.single().url)
        assertEquals("혼자", item.tags.single().label)
        assertEquals("좋아요", item.aiSummary?.pros)
        assertTrue(item.placeFavorite)
    }

    @Test
    fun `좌표 없는 목록의 distanceMeters는 null 그대로다`() {
        every { lookup.findPhotoRows(any()) } returns emptyList()
        every { lookup.findTagRows(any()) } returns emptyList()
        every { lookup.findSummaryRows(any()) } returns emptyList()

        assertNull(composer.compose(listOf(row(1, distance = null))).single().distanceMeters)
    }

    @Test
    fun `빈 입력이면 부속 조회를 하지 않는다`() {
        assertEquals(emptyList(), composer.compose(emptyList()))
    }
}
