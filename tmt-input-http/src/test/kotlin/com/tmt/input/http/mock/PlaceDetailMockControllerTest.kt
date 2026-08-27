package com.tmt.input.http.mock

import com.tmt.input.http.auth.UserIdArgumentResolver
import com.tmt.input.http.exception.ExceptionAdvice
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class PlaceDetailMockControllerTest {
    private val placeStore = InMemoryStore<MockPlace>(idPrefix = "place")
    private val saveStore = InMemoryStore<MockSave>(idPrefix = "save")
    private val favoriteStore = MockFavoriteStore()
    private val userStore = MockUserStore(listOf(MockUser(1, "조용한 미식가", "tester1@example.com")))
    private val aiSummaryStore = MockAiSummaryStore()

    private val place = MockFixtures.place(placeStore, "오즈 커피", categoryName = "카페·디저트", phoneNumber = "010 5244 6041")

    private val mockMvc: MockMvc =
        MockMvcBuilders
            .standaloneSetup(
                PlaceDetailMockController(
                    placeStore,
                    saveStore,
                    favoriteStore,
                    ReviewCardAssembler(placeStore, favoriteStore, aiSummaryStore, userStore),
                ),
            ).setCustomArgumentResolvers(UserIdArgumentResolver())
            .setControllerAdvice(ExceptionAdvice())
            .build()

    @Test
    fun `가게 상세는 리뷰 집계와 리뷰 파생 사진을 내린다 (P7·P9)`() {
        MockFixtures.review(saveStore, place.placeId, reviewId = "review_1", rating = 4)
        MockFixtures.review(
            saveStore,
            place.placeId,
            reviewId = "review_2",
            rating = 5,
            photoAssetIds = listOf("asset_2"),
        )

        mockMvc
            .perform(get("/v1/places/${place.placeId}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("오즈 커피"))
            .andExpect(jsonPath("$.averageRating").value(4.5))
            .andExpect(jsonPath("$.reviewCount").value(2))
            .andExpect(jsonPath("$.photos.length()").value(2))
            .andExpect(jsonPath("$.photos[0].reviewId").isNotEmpty)
            .andExpect(jsonPath("$.phoneNumber").value("010 5244 6041"))
            .andExpect(jsonPath("$.isFavorite").value(false))
    }

    @Test
    fun `리뷰가 없으면 평균은 null이고 사진은 빈 배열이다`() {
        mockMvc
            .perform(get("/v1/places/${place.placeId}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.averageRating").doesNotExist())
            .andExpect(jsonPath("$.reviewCount").value(0))
            .andExpect(jsonPath("$.photos").isEmpty)
    }

    @Test
    fun `없는 매장은 PLACE_NOT_FOUND다`() {
        mockMvc
            .perform(get("/v1/places/place_999"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("PLACE_NOT_FOUND"))
    }

    @Test
    fun `가게 리뷰 목록은 최신순이다 (createdAt DESC)`() {
        MockFixtures.review(
            saveStore,
            place.placeId,
            reviewId = "review_1",
            createdAt = java.time.Instant.parse("2026-08-10T00:00:00Z"),
        )
        MockFixtures.review(
            saveStore,
            place.placeId,
            reviewId = "review_2",
            createdAt = java.time.Instant.parse("2026-08-12T00:00:00Z"),
        )

        mockMvc
            .perform(get("/v1/places/${place.placeId}/reviews"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items[0].reviewId").value("review_2"))
            .andExpect(jsonPath("$.items[1].reviewId").value("review_1"))
            .andExpect(jsonPath("$.items[0].distanceMeters").doesNotExist())
    }

    @Test
    fun `찜은 멱등 토글이고 카드 응답에 반영된다 (F2)`() {
        mockMvc
            .perform(put("/v1/places/${place.placeId}/favorite").header(UserIdArgumentResolver.HEADER, "1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.isFavorite").value(true))

        // 이미 찜한 매장에 다시 보내도 200
        mockMvc
            .perform(put("/v1/places/${place.placeId}/favorite").header(UserIdArgumentResolver.HEADER, "1"))
            .andExpect(status().isOk)

        mockMvc
            .perform(get("/v1/places/${place.placeId}").header(UserIdArgumentResolver.HEADER, "1"))
            .andExpect(jsonPath("$.isFavorite").value(true))

        mockMvc
            .perform(delete("/v1/places/${place.placeId}/favorite").header(UserIdArgumentResolver.HEADER, "1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.isFavorite").value(false))

        // 찜하지 않은 매장에 DELETE를 보내도 200
        mockMvc
            .perform(delete("/v1/places/${place.placeId}/favorite").header(UserIdArgumentResolver.HEADER, "1"))
            .andExpect(status().isOk)
    }

    @Test
    fun `찜은 인증 필수다`() {
        mockMvc
            .perform(put("/v1/places/${place.placeId}/favorite"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `없는 매장 찜은 PLACE_NOT_FOUND다`() {
        mockMvc
            .perform(put("/v1/places/place_999/favorite").header(UserIdArgumentResolver.HEADER, "1"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("PLACE_NOT_FOUND"))
    }
}
