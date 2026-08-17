package com.tmt.input.http.mock

import com.tmt.input.http.auth.UserIdArgumentResolver
import com.tmt.input.http.exception.ExceptionAdvice
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class SaveMockControllerTest {
    private val placeStore =
        InMemoryStore<MockPlace>(idPrefix = "place").apply {
            create { id -> MockPlace(id, "델리스피자", "서울 마포구 도화동 200-14", "마포구 도화동", "양식", 37.5399, 126.9515) }
        }
    private val assetStore = InMemoryStore<MockAsset>(idPrefix = "asset")
    private val saveStore = InMemoryStore<MockSave>(idPrefix = "save")

    private val mockMvc: MockMvc =
        MockMvcBuilders
            .standaloneSetup(
                SaveMockController(
                    saveStore,
                    placeStore,
                    assetStore,
                    MockTicketLedger(),
                    MockReviewIdGenerator(),
                    MockIdempotencyRegistry(),
                ),
            ).setCustomArgumentResolvers(UserIdArgumentResolver())
            .setControllerAdvice(ExceptionAdvice())
            .build()

    private val completeBody =
        """
        {
          "placeId": "place_1",
          "photoAssetIds": ["asset_1"],
          "companionTagIds": ["tag_couple"],
          "positivePointTagIds": ["tag_kind"],
          "rating": 5,
          "content": "맛있어요"
        }
        """.trimIndent()

    private fun postSave(
        body: String,
        userId: Long = 1,
        idempotencyKey: String? = "key-1",
    ) = mockMvc.perform(
        post("/v1/saves")
            .header(UserIdArgumentResolver.HEADER, userId.toString())
            .apply { idempotencyKey?.let { header(SaveMockController.IDEMPOTENCY_KEY_HEADER, it) } }
            .contentType(MediaType.APPLICATION_JSON)
            .content(body),
    )

    private fun ownedAsset(ownerId: Long = 1): MockAsset =
        assetStore.create { id -> MockAsset(assetId = id, ownerId = ownerId, contentType = "image/jpeg") }

    @Test
    fun `가게 선택만으로 작성 완료하면 저장만 생긴다 (C1·C5)`() {
        postSave("""{ "placeId": "place_1" }""")
            .andExpect(status().isCreated)
            .andExpect(header().string("Location", "/v1/saves/save_1"))
            .andExpect(jsonPath("$.saveId").value("save_1"))
            .andExpect(jsonPath("$.reviewId").doesNotExist())
            .andExpect(jsonPath("$.ticket.grantedCount").value(0))
            .andExpect(jsonPath("$.ticket.availableCount").value(1))
    }

    @Test
    fun `전 단계를 채우면 리뷰가 되고 티켓 1장이 나간다 (C4)`() {
        ownedAsset()

        postSave(completeBody)
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.reviewId").value("review_1"))
            .andExpect(jsonPath("$.ticket.grantedCount").value(1))
            .andExpect(jsonPath("$.ticket.availableCount").value(2))
    }

    @Test
    fun `본문이 공백뿐이면 리뷰가 되지 않는다 (C4 — 공백 제외 1자 이상)`() {
        ownedAsset()
        val body = completeBody.replace("맛있어요", "   ")

        postSave(body)
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.reviewId").doesNotExist())
            .andExpect(jsonPath("$.ticket.grantedCount").value(0))
    }

    @Test
    fun `Idempotency-Key 없이는 작성할 수 없다 (공통 규약 §9)`() {
        postSave("""{ "placeId": "place_1" }""", idempotencyKey = null)
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
    }

    @Test
    fun `같은 키에 같은 바디면 최초 응답을 재현하고 티켓을 다시 발급하지 않는다`() {
        ownedAsset()

        postSave(completeBody)
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.ticket.grantedCount").value(1))
            .andExpect(jsonPath("$.ticket.availableCount").value(2))

        // 최초 응답을 그대로 재현한다 — grantedCount를 0으로 다시 만들면 "이번 요청으로 몇 장
        // 나갔는지"가 최초 응답과 달라진다. 티켓이 두 번 나가지 않았다는 것은 availableCount가
        // 그대로 2인 것으로 확인된다 (3이 아니다)
        postSave(completeBody)
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.saveId").value("save_1"))
            .andExpect(jsonPath("$.ticket.grantedCount").value(1))
            .andExpect(jsonPath("$.ticket.availableCount").value(2))

        assertEquals(1, saveStore.findAll().size)
    }

    @Test
    fun `이어쓰기도 같은 키 재요청이면 최초 응답을 그대로 재현한다 (규약 §9)`() {
        ownedAsset()
        postSave("""{ "placeId": "place_1" }""").andExpect(status().isCreated)

        val put =
            put("/v1/saves/save_1")
                .header(UserIdArgumentResolver.HEADER, "1")
                .header(SaveMockController.IDEMPOTENCY_KEY_HEADER, "put-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(completeBody)

        // 최초: 리뷰 성립 + 티켓 1장
        mockMvc
            .perform(put)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.reviewId").value("review_1"))
            .andExpect(jsonPath("$.ticket.grantedCount").value(1))

        // 재시도: 409가 아니라 최초 응답 그대로 (grantedCount까지 동일)
        mockMvc
            .perform(put)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.reviewId").value("review_1"))
            .andExpect(jsonPath("$.ticket.grantedCount").value(1))
    }

    @Test
    fun `POST와 PUT은 같은 키를 써도 서로의 응답을 재현하지 않는다`() {
        postSave("""{ "placeId": "place_1" }""", idempotencyKey = "shared-key").andExpect(status().isCreated)

        // 같은 키지만 endpoint가 다르므로 PUT은 새 요청으로 처리된다
        mockMvc
            .perform(
                put("/v1/saves/save_1")
                    .header(UserIdArgumentResolver.HEADER, "1")
                    .header(SaveMockController.IDEMPOTENCY_KEY_HEADER, "shared-key")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{ "placeId": "place_1", "rating": 4 }"""),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.reviewId").doesNotExist())
    }

    @Test
    fun `같은 사진을 두 번 실어 보내면 VALIDATION_FAILED다`() {
        ownedAsset()

        postSave("""{ "placeId": "place_1", "photoAssetIds": ["asset_1", "asset_1"] }""")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
    }

    @Test
    fun `같은 키에 다른 바디면 IDEMPOTENCY_CONFLICT다`() {
        postSave("""{ "placeId": "place_1" }""").andExpect(status().isCreated)
        postSave("""{ "placeId": "place_1", "rating": 3 }""")
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"))
    }

    @Test
    fun `없는 매장이면 PLACE_NOT_FOUND다`() {
        postSave("""{ "placeId": "place_999" }""")
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("PLACE_NOT_FOUND"))
    }

    @Test
    fun `본문 500자 초과는 REVIEW_CONTENT_TOO_LONG이다`() {
        postSave("""{ "placeId": "place_1", "content": "${"가".repeat(501)}" }""")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("REVIEW_CONTENT_TOO_LONG"))
    }

    @Test
    fun `정의에 없는 태그는 REVIEW_TAG_NOT_FOUND다`() {
        postSave("""{ "placeId": "place_1", "companionTagIds": ["tag_ghost"] }""")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("REVIEW_TAG_NOT_FOUND"))
    }

    @Test
    fun `남의 asset을 붙이면 MEDIA_NOT_OWNED다`() {
        ownedAsset(ownerId = 99)

        postSave("""{ "placeId": "place_1", "photoAssetIds": ["asset_1"] }""")
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("MEDIA_NOT_OWNED"))
    }

    @Test
    fun `이미 다른 저장에 붙은 asset은 MEDIA_ALREADY_ATTACHED다`() {
        ownedAsset()
        postSave("""{ "placeId": "place_1", "photoAssetIds": ["asset_1"] }""").andExpect(status().isCreated)

        postSave("""{ "placeId": "place_1", "photoAssetIds": ["asset_1"] }""", idempotencyKey = "key-2")
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("MEDIA_ALREADY_ATTACHED"))
    }

    @Test
    fun `이어쓰기 목록에는 본인의 미완성 저장만 updatedAt 역순으로 내려간다 (C5·R8)`() {
        ownedAsset()
        postSave("""{ "placeId": "place_1" }""", idempotencyKey = "key-a").andExpect(status().isCreated)
        postSave(completeBody, idempotencyKey = "key-b").andExpect(status().isCreated)
        postSave("""{ "placeId": "place_1", "rating": 4 }""", idempotencyKey = "key-c").andExpect(status().isCreated)
        postSave("""{ "placeId": "place_1" }""", userId = 2, idempotencyKey = "key-d").andExpect(status().isCreated)

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
        postSave("""{ "placeId": "place_1" }""").andExpect(status().isCreated)

        mockMvc
            .perform(get("/v1/saves/save_1").header(UserIdArgumentResolver.HEADER, "2"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("SAVE_NOT_FOUND"))
    }

    @Test
    fun `리뷰가 된 저장의 상세에는 AI 요약이 붙는다 (A1)`() {
        ownedAsset()
        postSave(completeBody).andExpect(status().isCreated)

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

    @Test
    fun `이어쓰기로 전 단계를 채우면 리뷰로 전환된다 (C6)`() {
        ownedAsset()
        postSave("""{ "placeId": "place_1" }""").andExpect(status().isCreated)

        mockMvc
            .perform(
                put("/v1/saves/save_1")
                    .header(UserIdArgumentResolver.HEADER, "1")
                    .header(SaveMockController.IDEMPOTENCY_KEY_HEADER, "key-2")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(completeBody),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.saveId").value("save_1"))
            .andExpect(jsonPath("$.reviewId").value("review_1"))
            .andExpect(jsonPath("$.ticket.grantedCount").value(1))
    }

    @Test
    fun `이미 리뷰가 된 저장은 수정할 수 없다 (S4·R2)`() {
        ownedAsset()
        postSave(completeBody).andExpect(status().isCreated)

        mockMvc
            .perform(
                put("/v1/saves/save_1")
                    .header(UserIdArgumentResolver.HEADER, "1")
                    .header(SaveMockController.IDEMPOTENCY_KEY_HEADER, "key-2")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(completeBody),
            ).andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("SAVE_ALREADY_REVIEWED"))
    }

    @Test
    fun `이어쓰기에서 매장은 바꿀 수 없다 (S6)`() {
        placeStore.create { id -> MockPlace(id, "오즈 커피", "서울 마포구 도화동 201-1", "마포구 도화동", "카페·디저트", 37.5401, 126.9520) }
        postSave("""{ "placeId": "place_1" }""").andExpect(status().isCreated)

        mockMvc
            .perform(
                put("/v1/saves/save_1")
                    .header(UserIdArgumentResolver.HEADER, "1")
                    .header(SaveMockController.IDEMPOTENCY_KEY_HEADER, "key-2")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{ "placeId": "place_2" }"""),
            ).andExpect(status().isUnprocessableEntity)
            .andExpect(jsonPath("$.code").value("SAVE_PLACE_IMMUTABLE"))
    }
}
