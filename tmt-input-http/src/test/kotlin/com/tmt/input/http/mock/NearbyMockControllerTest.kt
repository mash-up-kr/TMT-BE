package com.tmt.input.http.mock

import com.tmt.input.http.auth.UserIdArgumentResolver
import com.tmt.input.http.exception.ExceptionAdvice
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class NearbyMockControllerTest {
    private val placeStore = InMemoryStore<MockPlace>(idPrefix = "place")
    private val saveStore = InMemoryStore<MockSave>(idPrefix = "save")
    private val favoriteStore = MockFavoriteStore()
    private val userStore = MockUserStore(listOf(MockUser(1, "조용한 미식가", "tester1@example.com")))
    private val aiSummaryStore = MockAiSummaryStore()

    // 기준점(37.5399, 126.9515) / 800m쯤 북쪽 / 5km 밖 세 곳
    private val near = MockFixtures.place(placeStore, "가까운 집", 37.5399, 126.9515)
    private val edge = MockFixtures.place(placeStore, "경계 집", 37.5471, 126.9515)
    private val far = MockFixtures.place(placeStore, "먼 집", 37.60, 126.9515)

    private val mockMvc: MockMvc =
        MockMvcBuilders
            .standaloneSetup(
                NearbyMockController(
                    saveStore,
                    placeStore,
                    ReviewCardAssembler(placeStore, favoriteStore, aiSummaryStore, userStore),
                ),
            ).setCustomArgumentResolvers(UserIdArgumentResolver())
            .setControllerAdvice(ExceptionAdvice())
            .build()

    @Test
    fun `좌표가 없으면 VALIDATION_FAILED다`() {
        mockMvc
            .perform(get("/v1/nearby/reviews"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
    }

    @Test
    fun `반경 1km 안의 리뷰만 거리순으로 내린다 (E1·E2)`() {
        MockFixtures.review(saveStore, edge.placeId, reviewId = "review_1")
        MockFixtures.review(saveStore, near.placeId, reviewId = "review_2")
        MockFixtures.review(saveStore, far.placeId, reviewId = "review_3")

        mockMvc
            .perform(get("/v1/nearby/reviews").param("latitude", "37.5399").param("longitude", "126.9515"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.items[0].reviewId").value("review_2"))
            .andExpect(jsonPath("$.items[0].distanceMeters").value(0))
            .andExpect(jsonPath("$.items[0].author.userId").value("user_1"))
            .andExpect(jsonPath("$.items[0].place.regionName").value("마포구 도화동"))
            .andExpect(jsonPath("$.items[1].reviewId").value("review_1"))
    }

    @Test
    fun `미완성 저장은 피드에 나오지 않는다 (R8)`() {
        saveStore.create { id ->
            MockSave(
                id,
                1,
                near.placeId,
                emptyList(),
                emptyList(),
                emptyList(),
                null,
                null,
                null,
                java.time.Instant.now(),
                java.time.Instant.now(),
            )
        }

        mockMvc
            .perform(get("/v1/nearby/reviews").param("latitude", "37.5399").param("longitude", "126.9515"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items").isEmpty)
    }

    @Test
    fun `지도형 핀은 viewport가 필수다`() {
        mockMvc
            .perform(get("/v1/nearby/places"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
    }

    @Test
    fun `지도형 핀은 리뷰가 1건 이상 있는 매장만 내린다 (E6)`() {
        MockFixtures.review(saveStore, near.placeId, reviewId = "review_1")
        MockFixtures.review(saveStore, near.placeId, reviewId = "review_2")

        mockMvc
            .perform(
                get("/v1/nearby/places")
                    .param("north", "38.0")
                    .param("south", "37.0")
                    .param("east", "127.5")
                    .param("west", "126.5"),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].placeId").value(near.placeId))
            .andExpect(jsonPath("$.items[0].reviewCount").value(2))
            .andExpect(jsonPath("$.truncated").value(false))
    }

    @Test
    fun `지도 핀 검색어는 목록과 같은 대상을 본다 — 주소로도 찾힌다 (E9)`() {
        MockFixtures.review(saveStore, near.placeId, reviewId = "review_1")

        // near 픽스처의 주소가 "서울 마포구 도화동 200-14"라 가게명이 아닌 주소로도 걸려야 한다
        mockMvc
            .perform(
                get("/v1/nearby/places")
                    .param("north", "38.0")
                    .param("south", "37.0")
                    .param("east", "127.5")
                    .param("west", "126.5")
                    .param("query", "도화동"),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].placeId").value(near.placeId))
    }

    @Test
    fun `viewport 밖 매장은 핀에서 빠진다`() {
        MockFixtures.review(saveStore, near.placeId, reviewId = "review_1")
        MockFixtures.review(saveStore, far.placeId, reviewId = "review_2")

        mockMvc
            .perform(
                get("/v1/nearby/places")
                    .param("north", "37.55")
                    .param("south", "37.50")
                    .param("east", "127.0")
                    .param("west", "126.9"),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].placeId").value(near.placeId))
    }
}
