package com.tmt.input.http.controller

import com.tmt.application.port.input.FavoriteKey
import com.tmt.application.port.input.FavoritePlaceView
import com.tmt.application.port.input.FavoriteSlice
import com.tmt.application.port.input.GetTicketHistoryUseCase
import com.tmt.application.port.input.GetUserFavoritesUseCase
import com.tmt.application.port.input.GetUserGroupsUseCase
import com.tmt.application.port.input.GetUserProfileUseCase
import com.tmt.application.port.input.GetUserReviewGridUseCase
import com.tmt.application.port.input.GroupCardSlice
import com.tmt.application.port.input.GroupCardView
import com.tmt.application.port.input.JoinedGroupKey
import com.tmt.application.port.input.JoinedGroupView
import com.tmt.application.port.input.ReviewGridItemView
import com.tmt.application.port.input.ReviewGridKey
import com.tmt.application.port.input.ReviewGridSlice
import com.tmt.application.port.input.TicketHistoryItemType
import com.tmt.application.port.input.TicketHistoryItemView
import com.tmt.application.port.input.TicketHistoryKey
import com.tmt.application.port.input.TicketHistorySlice
import com.tmt.application.port.input.UserProfileView
import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException
import com.tmt.input.http.auth.UserIdArgumentResolver
import com.tmt.input.http.exception.ExceptionAdvice
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UserControllerTest {
    private val stub = StubUserPageUseCases()

    private val mockMvc: MockMvc =
        MockMvcBuilders
            .standaloneSetup(UserController(stub, stub, stub, stub, stub))
            .setCustomArgumentResolvers(UserIdArgumentResolver())
            .setControllerAdvice(ExceptionAdvice())
            .build()

    @Test
    fun `마이페이지 상단은 인증이 없으면 401이다`() {
        mockMvc
            .perform(get("/v1/users/me"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `마이페이지 상단은 표기 접두와 티켓 수를 내린다`() {
        mockMvc
            .perform(get("/v1/users/me").header(UserIdArgumentResolver.HEADER, "7"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.userId").value("user_7"))
            .andExpect(jsonPath("$.nickname").value("준형이"))
            .andExpect(jsonPath("$.availableTicketCount").value(4))
            .andExpect(jsonPath("$.reviewCount").value(3))
    }

    @Test
    fun `내 리뷰 탭 항목에 saveId가 있고 접두 표기를 쓴다`() {
        mockMvc
            .perform(get("/v1/users/me/reviews").header(UserIdArgumentResolver.HEADER, "7"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items[0].reviewId").value("rv_1"))
            .andExpect(jsonPath("$.items[0].saveId").value("save_11"))
            .andExpect(jsonPath("$.items[0].place.placeId").value("place_5"))
            .andExpect(jsonPath("$.hasNext").value(false))
    }

    @Test
    fun `타인 리뷰 탭 항목에는 saveId가 없다`() {
        mockMvc
            .perform(get("/v1/users/user_7/reviews"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items[0].reviewId").value("rv_1"))
            .andExpect(jsonPath("$.items[0].saveId").doesNotExist())

        assertEquals(7L, stub.reviewCalls.single())
    }

    @Test
    fun `경로의 사용자 ID는 표기와 숫자를 둘 다 받는다`() {
        mockMvc.perform(get("/v1/users/user_7")).andExpect(status().isOk)
        mockMvc.perform(get("/v1/users/7")).andExpect(status().isOk)

        mockMvc
            .perform(get("/v1/users/abc"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
    }

    @Test
    fun `타인 프로필에는 email과 티켓 수가 없다`() {
        mockMvc
            .perform(get("/v1/users/user_7"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.userId").value("user_7"))
            .andExpect(jsonPath("$.email").doesNotExist())
            .andExpect(jsonPath("$.availableTicketCount").doesNotExist())
    }

    @Test
    fun `없는 사용자는 404 USER_NOT_FOUND다`() {
        stub.profileError = TmtException(ErrorCode.USER_NOT_FOUND)

        mockMvc
            .perform(get("/v1/users/user_404"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
    }

    @Test
    fun `타인 그룹 탭은 인증 없이 열리고 viewer가 null로 전달된다`() {
        mockMvc
            .perform(get("/v1/users/user_7/groups"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items[0].groupId").value("group_3"))

        assertNull(stub.groupCalls.single())
    }

    @Test
    fun `내 그룹 탭은 본인이 viewer다`() {
        mockMvc
            .perform(get("/v1/users/me/groups").header(UserIdArgumentResolver.HEADER, "7"))
            .andExpect(status().isOk)

        assertEquals(7L, stub.groupCalls.single())
    }

    @Test
    fun `좋아요 탭은 PlaceCard 모양으로 내린다`() {
        mockMvc
            .perform(get("/v1/users/me/favorites").header(UserIdArgumentResolver.HEADER, "7"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items[0].placeId").value("place_5"))
            .andExpect(jsonPath("$.items[0].isFavorite").value(true))
            .andExpect(jsonPath("$.items[0].averageRating").value(4.7))
            .andExpect(jsonPath("$.items[0].categoryId").value("cat_korean"))
    }

    @Test
    fun `내 티켓은 잔액·작성 중 건수·이력을 함께 내리고 이력 행은 증감이 있다`() {
        mockMvc
            .perform(get("/v1/users/me/tickets").header(UserIdArgumentResolver.HEADER, "7"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.availableCount").value(4))
            .andExpect(jsonPath("$.inProgressSaveCount").value(1))
            .andExpect(jsonPath("$.items[0].entryId").value("tkh_g21"))
            .andExpect(jsonPath("$.items[0].type").value("REVIEW_REWARD"))
            .andExpect(jsonPath("$.items[0].amount").value(1))
            .andExpect(jsonPath("$.items[0].saveId").value("save_21"))
            .andExpect(jsonPath("$.items[0].place.placeId").value("place_5"))
            .andExpect(jsonPath("$.items[0].group").doesNotExist())
    }

    @Test
    fun `사진 없는 리뷰는 그리드에서 thumbnailUrl이 null로 내려간다`() {
        stub.reviewThumbnail = null

        mockMvc
            .perform(get("/v1/users/me/reviews").header(UserIdArgumentResolver.HEADER, "7"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items[0].reviewId").value("rv_1"))
            .andExpect(jsonPath("$.items[0].thumbnailUrl").value(null))
    }

    @Test
    fun `다음 커서로 다음 페이지를 요청하면 유스케이스가 after 키를 받는다`() {
        stub.reviewHasNext = true
        val cursor =
            mockMvc
                .perform(get("/v1/users/me/reviews").header(UserIdArgumentResolver.HEADER, "7"))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andReturn()
                .response.contentAsString
                .substringAfter("\"nextCursor\":\"")
                .substringBefore('"')

        mockMvc
            .perform(
                get("/v1/users/me/reviews")
                    .header(UserIdArgumentResolver.HEADER, "7")
                    .param("cursor", cursor),
            ).andExpect(status().isOk)

        assertEquals(ReviewGridKey(Instant.parse("2026-08-01T00:00:00Z"), 1L), stub.reviewAfterKeys.last())
    }

    @Test
    fun `다른 사용자의 리뷰 커서는 INVALID_CURSOR다`() {
        stub.reviewHasNext = true
        val cursor =
            mockMvc
                .perform(get("/v1/users/me/reviews").header(UserIdArgumentResolver.HEADER, "7"))
                .andReturn()
                .response.contentAsString
                .substringAfter("\"nextCursor\":\"")
                .substringBefore('"')

        mockMvc
            .perform(get("/v1/users/user_8/reviews").param("cursor", cursor))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_CURSOR"))
    }

    private class StubUserPageUseCases :
        GetUserProfileUseCase,
        GetUserReviewGridUseCase,
        GetUserGroupsUseCase,
        GetUserFavoritesUseCase,
        GetTicketHistoryUseCase {
        var profileError: TmtException? = null
        var reviewHasNext = false
        var reviewThumbnail: String? = "https://media.example.com/m.jpg"
        val reviewCalls = mutableListOf<Long>()
        val reviewAfterKeys = mutableListOf<ReviewGridKey?>()
        val groupCalls = mutableListOf<Long?>()

        override fun getMine(userId: Long): UserProfileView = profile(userId, mine = true)

        override fun getOther(targetUserId: Long): UserProfileView {
            profileError?.let { throw it }
            return profile(targetUserId, mine = false)
        }

        private fun profile(
            userId: Long,
            mine: Boolean,
        ) = UserProfileView(
            userId = userId,
            nickname = "준형이",
            profileImageUrl = null,
            reviewCount = 3,
            joinedGroupCount = 2,
            favoritePlaceCount = 5,
            availableTicketCount = if (mine) 4 else null,
            email = null,
        )

        override fun list(
            targetUserId: Long,
            after: ReviewGridKey?,
            limit: Int,
        ): ReviewGridSlice {
            reviewCalls += targetUserId
            reviewAfterKeys += after
            return ReviewGridSlice(
                items =
                    listOf(
                        ReviewGridItemView(
                            reviewId = 1L,
                            saveId = 11L,
                            thumbnailUrl = reviewThumbnail,
                            placeId = 5L,
                            placeName = "김밥천국",
                            placeCategoryName = "한식",
                            createdAt = Instant.parse("2026-08-01T00:00:00Z"),
                        ),
                    ),
                hasNext = reviewHasNext,
            )
        }

        override fun list(
            targetUserId: Long,
            viewerId: Long?,
            after: JoinedGroupKey?,
            limit: Int,
        ): GroupCardSlice {
            groupCalls += viewerId
            return GroupCardSlice(
                items =
                    listOf(
                        JoinedGroupView(
                            card =
                                GroupCardView(
                                    groupId = 3L,
                                    name = "매콤단짝",
                                    oneLineDescription = "맵부심 모임",
                                    coverImageUrl = null,
                                    memberCount = 4,
                                    reviewCount = 10,
                                    placeCount = 6,
                                    matchedSavedPlaceCount = 2,
                                ),
                            joinedAt = Instant.parse("2026-07-01T00:00:00Z"),
                        ),
                    ),
                hasNext = false,
            )
        }

        override fun list(
            targetUserId: Long,
            viewerId: Long?,
            latitude: Double?,
            longitude: Double?,
            after: FavoriteKey?,
            limit: Int,
        ): FavoriteSlice =
            FavoriteSlice(
                items =
                    listOf(
                        FavoritePlaceView(
                            placeId = 5L,
                            name = "김밥천국",
                            roadAddress = "서울 마포구 오목로 1",
                            regionName = "마포구 도화동",
                            categoryId = "cat_korean",
                            categoryName = "한식",
                            averageRating = 4.7,
                            reviewCount = 3,
                            thumbnailUrl = null,
                            distanceMeters = null,
                            isFavorite = true,
                            favoritedAt = Instant.parse("2026-08-01T00:00:00Z"),
                        ),
                    ),
                hasNext = false,
            )

        override fun list(
            userId: Long,
            after: TicketHistoryKey?,
            limit: Int,
        ): TicketHistorySlice =
            TicketHistorySlice(
                availableCount = 4,
                inProgressSaveCount = 1,
                items =
                    listOf(
                        TicketHistoryItemView(
                            entryId = "tkh_g21",
                            type = TicketHistoryItemType.REVIEW_REWARD,
                            amount = 1,
                            saveId = 21L,
                            place = TicketHistoryItemView.PlaceRefView(5L, "김밥천국", "서울 마포구 오목로 1"),
                            group = null,
                            occurredAt = Instant.parse("2026-08-02T00:00:00Z"),
                        ),
                    ),
                hasNext = false,
            )
    }
}
