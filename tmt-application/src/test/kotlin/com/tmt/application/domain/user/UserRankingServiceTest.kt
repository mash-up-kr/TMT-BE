package com.tmt.application.domain.user

import com.tmt.application.domain.media.MediaUrlResolver
import com.tmt.application.port.input.UserRankingKey
import com.tmt.application.port.input.UserRankingsRequest
import com.tmt.application.port.output.persistence.UserRankingQueryPort
import com.tmt.application.port.output.persistence.UserRankingRow
import com.tmt.application.port.output.persistence.UserRankingsQuery
import com.tmt.application.port.output.persistence.UserRankingsSlice
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class UserRankingServiceTest {
    private var lastQuery: UserRankingsQuery? = null
    private var slice = UserRankingsSlice(rows = emptyList(), hasNext = false)

    private val port =
        object : UserRankingQueryPort {
            override fun findUserRankings(query: UserRankingsQuery): UserRankingsSlice {
                lastQuery = query
                return slice
            }
        }

    private val service = UserRankingService(port, MediaUrlResolver("https://cdn.example.com"))

    @Test
    fun `업로드한 프로필 사진이 카카오 URL보다 앞선다`() {
        slice =
            UserRankingsSlice(
                rows =
                    listOf(
                        row(userId = 1, s3Key = "profile/1.jpg", legacyUrl = "https://kakao.example/1.png"),
                        row(userId = 2, s3Key = null, legacyUrl = "https://kakao.example/2.png"),
                        row(userId = 3, s3Key = null, legacyUrl = null),
                    ),
                hasNext = false,
            )

        val urls = service.get(UserRankingsRequest(after = null, limit = 20)).items.map { it.profileImageUrl }

        assertEquals(listOf("https://cdn.example.com/profile/1.jpg", "https://kakao.example/2.png", null), urls)
    }

    @Test
    fun `커서와 limit을 조회 포트에 그대로 넘기고 정렬 키를 돌려준다`() {
        val key = UserRankingKey(reviewCount = 4, userId = 9)
        slice = UserRankingsSlice(rows = listOf(row(userId = 9)), hasNext = true, lastKey = key)

        val result = service.get(UserRankingsRequest(after = key, limit = 5))

        assertEquals(key, requireNotNull(lastQuery).after)
        assertEquals(5, requireNotNull(lastQuery).limit)
        assertEquals(key, result.lastKey)
        assertEquals(true, result.hasNext)
    }

    private fun row(
        userId: Long,
        s3Key: String? = null,
        legacyUrl: String? = null,
    ) = UserRankingRow(
        userId = userId,
        nickname = "유저$userId",
        profileImageUrl = legacyUrl,
        profileImageS3Key = s3Key,
        reviewCount = 4,
        sharedReviewCount = 0,
    )
}
