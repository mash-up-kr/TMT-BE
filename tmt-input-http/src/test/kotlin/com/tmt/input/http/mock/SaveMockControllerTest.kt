package com.tmt.input.http.mock

import com.tmt.input.http.auth.UserIdArgumentResolver
import com.tmt.input.http.exception.ExceptionAdvice
import com.tmt.input.http.idempotency.IdempotencyKeyArgumentResolver
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Instant

/**
 * 작성 완료(POST)는 실구현으로 옮겨 SaveControllerTest가 덮는다 (TMT-224).
 * 여기는 아직 mock으로 남은 이어쓰기(PUT)·목록·본인 상세만 본다.
 */
class SaveMockControllerTest {
    private val placeStore =
        InMemoryStore<MockPlace>(idPrefix = "place").apply {
            create { id -> MockPlace(id, "델리스피자", "서울 마포구 도화동 200-14", "마포구 도화동", "양식", 37.5399, 126.9515) }
        }
    private val attachMedia = FakeAttachMediaUseCase()
    private val saveStore = InMemoryStore<MockSave>(idPrefix = "save")
    private val aiSummaryStore = MockAiSummaryStore()

    private val mockMvc: MockMvc =
        MockMvcBuilders
            .standaloneSetup(
                SaveMockController(
                    mockSaveStore = saveStore,
                    mockPlaceStore = placeStore,
                    attachMediaUseCase = attachMedia,
                    mockMediaUrls = fakeMockMediaUrls(),
                    mockTicketLedger = MockTicketLedger(),
                    mockReviewIdGenerator = MockReviewIdGenerator(),
                    mockIdempotencyRegistry = MockIdempotencyRegistry(),
                    mockAiSummaryStore = aiSummaryStore,
                ),
            ).setCustomArgumentResolvers(UserIdArgumentResolver(), IdempotencyKeyArgumentResolver())
            .setControllerAdvice(ExceptionAdvice())
            .build()

    private val completeBody =
        """
        {
          "placeId": "place_1",
          "photoAssetIds": ["1"],
          "companionTagIds": ["tag_couple"],
          "positivePointTagIds": ["tag_kind"],
          "rating": 5,
          "content": "맛있어요"
        }
        """.trimIndent()

    /** 실구현 발급 assetId는 숫자다 (TMT-202). 시드의 `asset_*`는 발급분이 아니라 여기 쓰지 않는다. */
    private fun ownedAsset(
        assetId: Long = 1,
        ownerId: Long = 1,
    ): String = attachMedia.issue(assetId = assetId, ownerId = ownerId)

    /** POST가 실구현으로 빠졌으므로 이어쓰기의 출발점은 스토어에 직접 심는다. */
    private fun seedSave(
        ownerId: Long = 1,
        placeId: String = "place_1",
        photoAssetIds: List<String> = emptyList(),
        rating: Int? = null,
        content: String? = null,
        reviewId: String? = null,
        updatedAt: Instant = Instant.parse("2026-08-27T00:00:00Z"),
    ): MockSave =
        saveStore.create { id ->
            MockSave(
                saveId = id,
                ownerId = ownerId,
                placeId = placeId,
                photoAssetIds = photoAssetIds,
                companionTagIds = emptyList(),
                positivePointTagIds = emptyList(),
                rating = rating,
                content = content,
                reviewId = reviewId,
                createdAt = updatedAt,
                updatedAt = updatedAt,
            )
        }

    private fun putSave(
        saveId: String,
        body: String,
        userId: Long = 1,
        idempotencyKey: String = "put-1",
    ) = mockMvc.perform(
        put("/v1/saves/$saveId")
            .header(UserIdArgumentResolver.HEADER, userId.toString())
            .header(IdempotencyKeyArgumentResolver.HEADER, idempotencyKey)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body),
    )

    @Test
    fun `이어쓰기로 전 단계를 채우면 리뷰로 전환된다 (C6)`() {
        ownedAsset()
        seedSave()

        putSave("save_1", completeBody)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.saveId").value("save_1"))
            .andExpect(jsonPath("$.reviewId").value("review_1"))
            .andExpect(jsonPath("$.ticket.grantedCount").value(1))
    }

    @Test
    fun `이어쓰기도 같은 키 재요청이면 최초 응답을 그대로 재현한다 (규약 §9)`() {
        ownedAsset()
        seedSave()

        putSave("save_1", completeBody)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.reviewId").value("review_1"))
            .andExpect(jsonPath("$.ticket.grantedCount").value(1))

        // 재시도: 409가 아니라 최초 응답 그대로 (grantedCount까지 동일)
        putSave("save_1", completeBody)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.reviewId").value("review_1"))
            .andExpect(jsonPath("$.ticket.grantedCount").value(1))
    }

    @Test
    fun `같은 키에 다른 바디면 IDEMPOTENCY_CONFLICT다`() {
        seedSave()

        putSave("save_1", """{ "placeId": "place_1" }""").andExpect(status().isOk)
        putSave("save_1", """{ "placeId": "place_1", "rating": 3 }""")
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"))
    }

    @Test
    fun `이미 리뷰가 된 저장은 수정할 수 없다 (S4·R2)`() {
        ownedAsset()
        seedSave(reviewId = "review_1")

        putSave("save_1", completeBody)
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("SAVE_ALREADY_REVIEWED"))
    }

    @Test
    fun `이어쓰기에서 매장은 바꿀 수 없다 (S6)`() {
        placeStore.create { id -> MockPlace(id, "오즈 커피", "서울 마포구 도화동 201-1", "마포구 도화동", "카페·디저트", 37.5401, 126.9520) }
        seedSave()

        putSave("save_1", """{ "placeId": "place_2" }""")
            .andExpect(status().isUnprocessableEntity)
            .andExpect(jsonPath("$.code").value("SAVE_PLACE_IMMUTABLE"))
    }

    @Test
    fun `이어쓰기에 newPlace를 보내면 SAVE_PLACE_IMMUTABLE이다 (S6)`() {
        seedSave()

        putSave("save_1", """{ "newPlace": { "name": "한판승부", "addressId": "token" } }""")
            .andExpect(status().isUnprocessableEntity)
            .andExpect(jsonPath("$.code").value("SAVE_PLACE_IMMUTABLE"))
    }

    @Test
    fun `이어쓰기에서 남의 asset을 붙이면 MEDIA_NOT_OWNED다`() {
        ownedAsset(ownerId = 99)
        seedSave()

        putSave("save_1", """{ "placeId": "place_1", "photoAssetIds": ["1"] }""")
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("MEDIA_NOT_OWNED"))
    }

    @Test
    fun `타인의 저장은 이어쓸 수 없다 (S8)`() {
        seedSave(ownerId = 2)

        putSave("save_1", """{ "placeId": "place_1" }""")
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("SAVE_NOT_FOUND"))
    }

    @Test
    fun `이어쓰기 목록에는 본인의 미완성 저장만 updatedAt 역순으로 내려간다 (C5·R8)`() {
        seedSave(updatedAt = Instant.parse("2026-08-25T00:00:00Z"))
        seedSave(reviewId = "review_1", updatedAt = Instant.parse("2026-08-26T00:00:00Z"))
        seedSave(rating = 4, updatedAt = Instant.parse("2026-08-27T00:00:00Z"))
        seedSave(ownerId = 2, updatedAt = Instant.parse("2026-08-28T00:00:00Z"))

        mockMvc
            .perform(get("/v1/saves").header(UserIdArgumentResolver.HEADER, "1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.items[0].saveId").value("save_3"))
            .andExpect(jsonPath("$.items[0].place.name").value("델리스피자"))
            .andExpect(jsonPath("$.items[0].thumbnailUrl").doesNotExist())
            .andExpect(jsonPath("$.items[1].saveId").value("save_1"))
    }

    @Test
    fun `본인 상세는 소유자에게만 응답한다 (S8)`() {
        seedSave()

        mockMvc
            .perform(get("/v1/saves/save_1").header(UserIdArgumentResolver.HEADER, "2"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("SAVE_NOT_FOUND"))
    }

    @Test
    fun `방금 작성한 리뷰에는 AI 요약이 아직 없다 (A2)`() {
        ownedAsset()
        seedSave()
        putSave("save_1", completeBody).andExpect(status().isOk)

        // 요약은 리뷰 커밋 이후 별도로 생성된다 — 작성 직후 조회는 항상 null이다
        mockMvc
            .perform(get("/v1/saves/save_1").header(UserIdArgumentResolver.HEADER, "1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.reviewId").value("review_1"))
            .andExpect(jsonPath("$.aiSummary").doesNotExist())
    }

    @Test
    fun `요약이 생성된 뒤에는 상세에 붙는다 (A1)`() {
        ownedAsset()
        seedSave()
        putSave("save_1", completeBody).andExpect(status().isOk)
        aiSummaryStore.put("review_1", pros = "분위기가 좋아요", cons = "웨이팅이 많아요")

        mockMvc
            .perform(get("/v1/saves/save_1").header(UserIdArgumentResolver.HEADER, "1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.reviewId").value("review_1"))
            .andExpect(jsonPath("$.place.categoryName").value("양식"))
            .andExpect(jsonPath("$.photos[0].url").isNotEmpty)
            .andExpect(jsonPath("$.tags.length()").value(2))
            .andExpect(jsonPath("$.tags[0].label").value("연인"))
            .andExpect(jsonPath("$.aiSummary.pros").isNotEmpty)
            .andExpect(jsonPath("$.createdAt").isNotEmpty)
    }
}
