package com.tmt.application.domain.review

import com.tmt.application.domain.media.MediaUrlResolver
import com.tmt.application.port.output.persistence.PhotoRow
import com.tmt.application.port.output.persistence.SummaryRow
import com.tmt.application.port.output.persistence.TagRow
import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ReviewDetailServiceTest {
    private val authorId = 7L
    private val saveId = 10L

    private fun service(summaries: List<SummaryRow> = emptyList()): ReviewDetailService {
        val lookup =
            FakeReviewCardLookupPort(
                photos =
                    listOf(
                        PhotoRow(saveId, savePhotoId = 2, s3Key = "review/2.jpg", photoOrder = 1),
                        PhotoRow(saveId, savePhotoId = 1, s3Key = "review/1.jpg", photoOrder = 0),
                    ),
                tags = listOf(TagRow(saveId, "tag_couple", "연인")),
                summaries = summaries,
            )
        return ReviewDetailService(
            reviewQueryPort = FakeReviewQueryPort(details = mapOf(1L to reviewDetailRow(authorId = authorId))),
            reviewCardLookupPort = lookup,
            reviewCardComposer = ReviewCardComposer(lookup, MediaUrlResolver("https://cdn.example.com")),
        )
    }

    @Test
    fun `비로그인 조회는 isMine이 false다 (G2)`() {
        assertFalse(service().get(viewerId = null, reviewId = 1).isMine)
        assertFalse(service().get(viewerId = 99, reviewId = 1).isMine)
        assertTrue(service().get(viewerId = authorId, reviewId = 1).isMine)
    }

    @Test
    fun `사진은 photoOrder 순으로 나가고 URL은 카드와 같은 규칙을 쓴다`() {
        val photos = service().get(viewerId = null, reviewId = 1).photos

        assertEquals(listOf(0, 1), photos.map { it.order })
        assertEquals("https://cdn.example.com/review/1.jpg", photos.first().url)
    }

    @Test
    fun `요약이 생성되기 전에는 aiSummary가 null이다 (A2)`() {
        assertNull(service().get(viewerId = null, reviewId = 1).aiSummary)

        val filled = service(listOf(SummaryRow(1, "분위기가 좋아요", null))).get(viewerId = null, reviewId = 1)
        assertEquals("분위기가 좋아요", filled.aiSummary?.pros)
    }

    @Test
    fun `없거나 삭제된 리뷰는 REVIEW_NOT_FOUND다`() {
        val e = assertThrows<TmtException> { service().get(viewerId = null, reviewId = 999) }
        assertEquals(ErrorCode.REVIEW_NOT_FOUND, e.errorCode)
    }
}
