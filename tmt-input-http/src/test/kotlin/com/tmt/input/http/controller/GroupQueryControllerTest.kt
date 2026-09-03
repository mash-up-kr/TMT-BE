package com.tmt.input.http.controller

import com.tmt.application.port.input.GetGroupDetailUseCase
import com.tmt.application.port.input.GetGroupReviewsUseCase
import com.tmt.application.port.input.GroupDetailView
import com.tmt.application.port.input.GroupReviewsRequest
import com.tmt.application.port.input.GroupReviewsResult
import com.tmt.application.port.input.ReviewCardView
import com.tmt.input.http.auth.UserIdArgumentResolver
import com.tmt.input.http.exception.ExceptionAdvice
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Instant

/** 그룹 상세·리뷰 목록 실구현의 어댑터 계약 — 마스킹·게이트·커서가 mock과 같은지 지킨다. */
class GroupQueryControllerTest {
    private var lastRequest: GroupReviewsRequest? = null
    private var reviewsResult = GroupReviewsResult(items = emptyList(), gated = false, hasNext = false)

    private val detailUseCase = GetGroupDetailUseCase { _, _ -> detailView() }
    private val reviewsUseCase =
        GetGroupReviewsUseCase { request ->
            lastRequest = request
            reviewsResult
        }

    private val mockMvc: MockMvc =
        MockMvcBuilders
            .standaloneSetup(GroupQueryController(detailUseCase, reviewsUseCase))
            .setCustomArgumentResolvers(UserIdArgumentResolver())
            .setControllerAdvice(ExceptionAdvice())
            .build()

    @Test
    fun `상세가 mock과 같은 형태로 나간다 — 라벨·접두 ID·커버`() {
        mockMvc
            .perform(get("/v1/groups/group_7").header("X-User-Id", "1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.groupId").value("group_7"))
            .andExpect(jsonPath("$.foodCategory.label").value("한식"))
            .andExpect(jsonPath("$.regionTags[0].regionTagId").value("region_guro"))
            .andExpect(jsonPath("$.coverImages[0].reviewId").value("rv_3"))
    }

    @Test
    fun `미가입 응답은 본문과 단점 요약을 서버에서 지운다 (G1)`() {
        reviewsResult = GroupReviewsResult(items = listOf(card()), gated = true, hasNext = false)

        mockMvc
            .perform(get("/v1/groups/group_7/reviews"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items[0].content").value(nullValue()))
            .andExpect(jsonPath("$.items[0].aiSummary.pros").value("좋아요"))
            .andExpect(jsonPath("$.items[0].aiSummary.cons").value(nullValue()))
            .andExpect(jsonPath("$.items[0].contentLength").value(5))
            .andExpect(jsonPath("$.items[0].rating").value(5))
            .andExpect(jsonPath("$.gate.gated").value(true))
            .andExpect(jsonPath("$.gate.reason").value("MEMBERSHIP_REQUIRED"))
            .andExpect(jsonPath("$.gate.visibleCount").doesNotExist())
    }

    @Test
    fun `가입 응답은 본문이 그대로다`() {
        reviewsResult = GroupReviewsResult(items = listOf(card()), gated = false, hasNext = false)

        mockMvc
            .perform(get("/v1/groups/group_7/reviews").header("X-User-Id", "1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items[0].content").value("본문입니다"))
            .andExpect(jsonPath("$.items[0].aiSummary.cons").value("아쉬워요"))
            .andExpect(jsonPath("$.gate.gated").value(false))
    }

    @Test
    fun `다음 페이지가 있으면 커서를 발급하고 같은 그룹에서 되읽는다`() {
        reviewsResult = GroupReviewsResult(items = listOf(card()), gated = false, hasNext = true)

        val body =
            mockMvc
                .perform(get("/v1/groups/group_7/reviews").header("X-User-Id", "1"))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andReturn()
                .response.contentAsString
        val cursor = Regex("\"nextCursor\":\"([^\"]+)\"").find(body)!!.groupValues[1]

        mockMvc
            .perform(get("/v1/groups/group_7/reviews").header("X-User-Id", "1").param("cursor", cursor))
            .andExpect(status().isOk)
        assertEquals(9L, requireNotNull(requireNotNull(lastRequest).after).reviewId)

        // 다른 그룹에서 쓰면 무효
        mockMvc
            .perform(get("/v1/groups/group_8/reviews").header("X-User-Id", "1").param("cursor", cursor))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_CURSOR"))
    }

    @Test
    fun `형식이 어긋난 groupId는 GROUP_NOT_FOUND다`() {
        mockMvc
            .perform(get("/v1/groups/place_7/reviews"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("GROUP_NOT_FOUND"))
    }

    private fun card() =
        ReviewCardView(
            reviewId = 9L,
            authorId = 2L,
            authorNickname = "작성자",
            authorProfileImageUrl = null,
            rating = 5,
            distanceMeters = null,
            photos = emptyList(),
            aiSummary = ReviewCardView.AiSummary(pros = "좋아요", cons = "아쉬워요"),
            content = "본문입니다",
            tags = emptyList(),
            placeId = 1L,
            placeName = "가게",
            placeRegionName = "구로구 구로동",
            placeCategoryId = "cat_korean",
            placeCategoryName = "한식",
            placeFavorite = false,
            createdAt = Instant.parse("2026-08-20T00:00:00Z"),
        )

    private fun detailView() =
        GroupDetailView(
            groupId = 7L,
            name = "그룹",
            oneLineDescription = "한줄",
            description = null,
            imageUrl = null,
            coverImages = listOf(GroupDetailView.CoverImage("https://m/c.jpg", 3L)),
            memberCount = 3,
            reviewCount = 2,
            placeCount = 2,
            foodCategoryId = "cat_korean",
            regionTagIds = listOf("region_guro"),
            matchedSavedPlaceCount = 1,
            isMember = true,
            isOwner = false,
        )
}
