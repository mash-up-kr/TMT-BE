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
    private val mediaUrlResolver = MediaUrlResolver("https://cdn.example.com")

    private fun service(
        summaries: List<SummaryRow> = emptyList(),
        authorProfileImageUrl: String? = null,
        authorProfileImageS3Key: String? = null,
    ): ReviewDetailService {
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
            reviewQueryPort =
                FakeReviewQueryPort(
                    details =
                        mapOf(
                            1L to
                                reviewDetailRow(
                                    authorId = authorId,
                                    authorProfileImageUrl = authorProfileImageUrl,
                                    authorProfileImageS3Key = authorProfileImageS3Key,
                                ),
                        ),
                ),
            reviewCardLookupPort = lookup,
            reviewCardComposer = ReviewCardComposer(lookup, mediaUrlResolver),
            mediaUrlResolver = mediaUrlResolver,
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
    fun `요약 불가로 기록된 리뷰도 aiSummary가 null이다 (A2, TMT-392)`() {
        // 둘 다 null인 행은 "봤는데 요약할 내용이 없다"는 기록이지 요약이 아니다 — 미요약과 같은 응답
        assertNull(service(listOf(SummaryRow(1, null, null))).get(viewerId = null, reviewId = 1).aiSummary)
    }

    @Test
    fun `작성자 사진은 업로드한 것을 쓰고 없을 때만 카카오 URL로 돌아간다 (V7)`() {
        val uploaded =
            service(
                authorProfileImageUrl = "https://kakao.example/legacy.jpg",
                authorProfileImageS3Key = "profile/a.jpg",
            ).get(viewerId = null, reviewId = 1)
        assertEquals("https://cdn.example.com/profile/a.jpg", uploaded.author.profileImageUrl)

        val legacyOnly =
            service(authorProfileImageUrl = "https://kakao.example/legacy.jpg").get(viewerId = null, reviewId = 1)
        assertEquals("https://kakao.example/legacy.jpg", legacyOnly.author.profileImageUrl)

        assertNull(service().get(viewerId = null, reviewId = 1).author.profileImageUrl)
    }

    @Test
    fun `없거나 삭제된 리뷰는 REVIEW_NOT_FOUND다`() {
        val e = assertThrows<TmtException> { service().get(viewerId = null, reviewId = 999) }
        assertEquals(ErrorCode.REVIEW_NOT_FOUND, e.errorCode)
    }
}
