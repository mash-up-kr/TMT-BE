package com.tmt.input.http.mock

import com.tmt.input.http.auth.UserIdArgumentResolver
import com.tmt.input.http.exception.ExceptionAdvice
import org.hamcrest.Matchers.nullValue
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
import java.time.Instant

class GroupMockControllerTest {
    private val placeStore = InMemoryStore<MockPlace>(idPrefix = "place")
    private val saveStore = InMemoryStore<MockSave>(idPrefix = "save")
    private val attachMediaUseCase = FakeAttachMediaUseCase()
    private val groupStore = InMemoryStore<MockGroup>(idPrefix = "group")
    private val membershipStore = MockMembershipStore()
    private val shareStore = MockReviewShareStore()
    private val favoriteStore = MockFavoriteStore()
    private val userStore = MockUserStore(listOf(MockUser(1, "조용한 미식가", "tester1@example.com")))
    private val aiSummaryStore = MockAiSummaryStore()
    private val groupAssembler = GroupAssembler(fakeMockMediaUrls(), saveStore, membershipStore, shareStore)

    private val mockMvc: MockMvc =
        MockMvcBuilders
            .standaloneSetup(
                GroupMockController(
                    groupStore,
                    attachMediaUseCase,
                    membershipStore,
                    groupAssembler,
                    ReviewCardAssembler(fakeMockMediaUrls(), placeStore, favoriteStore, aiSummaryStore, userStore),
                ),
            ).setCustomArgumentResolvers(UserIdArgumentResolver())
            .setControllerAdvice(ExceptionAdvice())
            .build()

    private fun seedGroup(
        name: String = "성수 커피 탐험대",
        ownerId: Long = 999,
        oneLine: String = "조용히 커피 맛에 집중하는 사람들",
        foodCategoryId: String = "cat_cafe",
    ): MockGroup {
        val group =
            groupStore.create { id ->
                MockGroup(
                    id,
                    name,
                    oneLine,
                    null,
                    null,
                    foodCategoryId,
                    listOf("region_seongdong"),
                    ownerId,
                    Instant.now(),
                )
            }
        membershipStore.join(group.groupId, ownerId, group.createdAt)
        return group
    }

    private val createBody =
        """
        {
          "name": "나는야 초밥왕",
          "oneLineDescription": "회전 초밥부터 오마카세까지",
          "foodCategoryId": "cat_japanese",
          "regionTagIds": ["region_seoul_all"]
        }
        """.trimIndent()

    @Test
    fun `그룹을 만들면 생성자가 그룹장이자 멤버가 된다 (G13)`() {
        mockMvc
            .perform(
                post("/v1/groups")
                    .header(UserIdArgumentResolver.HEADER, "1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createBody),
            ).andExpect(status().isCreated)
            .andExpect(header().string("Location", "/v1/groups/group_1"))
            .andExpect(jsonPath("$.groupId").value("group_1"))
            .andExpect(jsonPath("$.isOwner").value(true))
            .andExpect(jsonPath("$.isMember").value(true))
            .andExpect(jsonPath("$.memberCount").value(1))
            .andExpect(jsonPath("$.foodCategory.label").value("일식"))
            .andExpect(jsonPath("$.regionTags[0].label").value("서울 전체"))
            .andExpect(jsonPath("$.coverImages").isEmpty)
    }

    @Test
    fun `같은 이름의 그룹은 만들 수 없다 (G6)`() {
        seedGroup(name = "나는야 초밥왕")

        mockMvc
            .perform(
                post("/v1/groups")
                    .header(UserIdArgumentResolver.HEADER, "1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createBody),
            ).andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("GROUP_NAME_DUPLICATED"))
    }

    @Test
    fun `지역 태그가 비어 있으면 VALIDATION_FAILED다 (G7)`() {
        mockMvc
            .perform(
                post("/v1/groups")
                    .header(UserIdArgumentResolver.HEADER, "1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createBody.replace("""["region_seoul_all"]""", "[]")),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
    }

    @Test
    fun `정의에 없는 태그는 GROUP_TAG_NOT_FOUND다`() {
        mockMvc
            .perform(
                post("/v1/groups")
                    .header(UserIdArgumentResolver.HEADER, "1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createBody.replace("cat_japanese", "cat_ghost")),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("GROUP_TAG_NOT_FOUND"))
    }

    @Test
    fun `이름 중복 확인은 참고값을 돌려준다`() {
        seedGroup(name = "성수 커피 탐험대")

        mockMvc
            .perform(
                get("/v1/groups/name-availability")
                    .header(UserIdArgumentResolver.HEADER, "1")
                    .param("name", "성수 커피 탐험대"),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.available").value(false))

        mockMvc
            .perform(
                get("/v1/groups/name-availability")
                    .header(UserIdArgumentResolver.HEADER, "1")
                    .param("name", "새 그룹"),
            ).andExpect(jsonPath("$.available").value(true))
    }

    @Test
    fun `탐색은 그룹명·한줄 소개·태그를 검색한다 (G18)`() {
        seedGroup(name = "성수 커피 탐험대")
        seedGroup(name = "나는야 초밥왕", oneLine = "회전 초밥부터 오마카세까지", foodCategoryId = "cat_japanese")

        mockMvc
            .perform(get("/v1/groups").param("query", "커피"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].name").value("성수 커피 탐험대"))

        // 태그 라벨(카페·디저트)로도 찾는다
        mockMvc
            .perform(get("/v1/groups").param("query", "카페"))
            .andExpect(jsonPath("$.items.length()").value(1))
    }

    @Test
    fun `지원하지 않는 sort 값은 VALIDATION_FAILED다`() {
        mockMvc
            .perform(get("/v1/groups").param("sort", "NEWEST"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
    }

    @Test
    fun `추천순은 내 저장 매장과 겹치는 그룹을 먼저 놓는다 (G17)`() {
        val cafe = seedGroup(name = "성수 커피 탐험대")
        val sushi = seedGroup(name = "나는야 초밥왕")
        // 초밥왕 그룹에 place_1 리뷰가 공유돼 있고, 나(user 1)도 place_1을 저장했다
        val place = MockFixtures.place(placeStore, "델리스피자")
        val review = MockFixtures.review(saveStore, place.placeId, ownerId = 999, reviewId = "review_1")
        shareStore.add(sushi.groupId, 999, review.reviewId!!)
        saveStore.create { id ->
            MockSave(
                id,
                1,
                place.placeId,
                emptyList(),
                emptyList(),
                emptyList(),
                null,
                null,
                null,
                Instant.now(),
                Instant.now(),
            )
        }

        mockMvc
            .perform(get("/v1/groups").header(UserIdArgumentResolver.HEADER, "1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items[0].groupId").value(sushi.groupId))
            .andExpect(jsonPath("$.items[0].matchedSavedPlaceCount").value(1))
            .andExpect(jsonPath("$.items[1].groupId").value(cafe.groupId))
    }

    @Test
    fun `그룹 리뷰 목록은 미가입이어도 전체를 페이징한다 (G1)`() {
        val group = seedGroup()
        val place = MockFixtures.place(placeStore, "델리스피자")
        (1..5).forEach { i ->
            val review = MockFixtures.review(saveStore, place.placeId, ownerId = 999, reviewId = "review_$i")
            shareStore.add(group.groupId, 999, review.reviewId!!)
        }

        // 미가입 (user 1) — 개수 제한이 없고 커서도 회원과 같게 채운다
        mockMvc
            .perform(get("/v1/groups/${group.groupId}/reviews?limit=3").header(UserIdArgumentResolver.HEADER, "1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(3))
            .andExpect(jsonPath("$.gate.gated").value(true))
            .andExpect(jsonPath("$.gate.reason").value("MEMBERSHIP_REQUIRED"))
            .andExpect(jsonPath("$.hasNext").value(true))
            .andExpect(jsonPath("$.nextCursor").isNotEmpty)

        // 가입자 (owner 999)
        mockMvc
            .perform(get("/v1/groups/${group.groupId}/reviews").header(UserIdArgumentResolver.HEADER, "999"))
            .andExpect(jsonPath("$.items.length()").value(5))
            .andExpect(jsonPath("$.gate.gated").value(false))
            .andExpect(jsonPath("$.gate.reason").value(nullValue()))
    }

    @Test
    fun `미가입 응답은 본문과 단점 요약을 서버에서 지운다 (G1)`() {
        val group = seedGroup()
        val place = MockFixtures.place(placeStore, "델리스피자")
        val review = MockFixtures.review(saveStore, place.placeId, ownerId = 999, reviewId = "review_1")
        shareStore.add(group.groupId, 999, review.reviewId!!)
        aiSummaryStore.put(review.reviewId!!, pros = "분위기가 좋아요", cons = "웨이팅이 길어요")

        // 미가입 — content는 null이고 길이만 남는다
        mockMvc
            .perform(get("/v1/groups/${group.groupId}/reviews").header(UserIdArgumentResolver.HEADER, "1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items[0].content").value(nullValue()))
            .andExpect(jsonPath("$.items[0].contentLength").value(4))
            .andExpect(jsonPath("$.items[0].aiSummary.cons").value(nullValue()))
            .andExpect(jsonPath("$.items[0].aiSummary.pros").value("분위기가 좋아요"))
            .andExpect(jsonPath("$.items[0].rating").value(5))
            .andExpect(jsonPath("$.items[0].photos.length()").value(1))

        // 가입자 — 그대로 보이고 contentLength는 같은 값이다
        mockMvc
            .perform(get("/v1/groups/${group.groupId}/reviews").header(UserIdArgumentResolver.HEADER, "999"))
            .andExpect(jsonPath("$.items[0].content").value("맛있어요"))
            .andExpect(jsonPath("$.items[0].contentLength").value(4))
            .andExpect(jsonPath("$.items[0].aiSummary.cons").value("웨이팅이 길어요"))
    }

    @Test
    fun `contentLength는 코드 포인트로 센다 — 이모지가 있어도 FE가 세는 값과 같다`() {
        val group = seedGroup()
        val place = MockFixtures.place(placeStore, "델리스피자")
        val review =
            saveStore.create { id ->
                MockSave(
                    saveId = id,
                    ownerId = 999,
                    placeId = place.placeId,
                    photoAssetIds = listOf("asset_1"),
                    companionTagIds = emptyList(),
                    positivePointTagIds = emptyList(),
                    rating = 5,
                    // 이모지는 하나가 UTF-16으로 2 — 코드 포인트로 세면 7, UTF-16으로 세면 9다
                    content = "맛있어요 🍕🍕",
                    reviewId = "review_1",
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                )
            }
        shareStore.add(group.groupId, 999, review.reviewId!!)

        mockMvc
            .perform(get("/v1/groups/${group.groupId}/reviews").header(UserIdArgumentResolver.HEADER, "1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items[0].contentLength").value(7))
    }

    @Test
    fun `편집은 생성자만 할 수 있다 (G13)`() {
        val group = seedGroup(ownerId = 999)

        mockMvc
            .perform(
                put("/v1/groups/${group.groupId}")
                    .header(UserIdArgumentResolver.HEADER, "1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createBody),
            ).andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("GROUP_OWNER_REQUIRED"))

        mockMvc
            .perform(
                put("/v1/groups/${group.groupId}")
                    .header(UserIdArgumentResolver.HEADER, "999")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createBody),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("나는야 초밥왕"))
    }

    @Test
    fun `대표 이미지로 쓴 asset은 ATTACHED가 된다 (M4·M7)`() {
        val assetId = attachMediaUseCase.issue(42, ownerId = 1)

        mockMvc
            .perform(
                post("/v1/groups")
                    .header(UserIdArgumentResolver.HEADER, "1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createBody.dropLast(1) + ""","imageAssetId": "$assetId" }"""),
            ).andExpect(status().isCreated)

        // STAGED로 남으면 TTL 정리가 사용 중인 그룹 이미지를 지운다
        assertEquals(true, attachMediaUseCase.isAttached(42))
    }

    @Test
    fun `남의 asset은 그룹 이미지로 쓸 수 없다 (M2)`() {
        attachMediaUseCase.issue(43, ownerId = 999)

        mockMvc
            .perform(
                post("/v1/groups")
                    .header(UserIdArgumentResolver.HEADER, "1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createBody.dropLast(1) + ""","imageAssetId": "43" }"""),
            ).andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("MEDIA_NOT_OWNED"))
    }

    @Test
    fun `접두가 붙은 옛 assetId는 없는 사진과 같다`() {
        mockMvc
            .perform(
                post("/v1/groups")
                    .header(UserIdArgumentResolver.HEADER, "1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createBody.dropLast(1) + ""","imageAssetId": "asset_1" }"""),
            ).andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("MEDIA_NOT_OWNED"))
    }

    @Test
    fun `이미 붙은 asset은 그룹 이미지로 다시 쓸 수 없다`() {
        attachMediaUseCase.issue(44, ownerId = 1)
        attachMediaUseCase.attach(listOf(44))

        mockMvc
            .perform(
                post("/v1/groups")
                    .header(UserIdArgumentResolver.HEADER, "1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createBody.dropLast(1) + ""","imageAssetId": "44" }"""),
            ).andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("MEDIA_ALREADY_ATTACHED"))
    }

    @Test
    fun `이미지를 교체하면 이전 asset은 STAGED로 돌아간다`() {
        attachMediaUseCase.issue(45, ownerId = 999)
        attachMediaUseCase.issue(46, ownerId = 999)
        attachMediaUseCase.attach(listOf(45))
        val group = seedGroup()
        groupStore.update(group.groupId) { it.copy(imageAssetId = "45") }

        mockMvc
            .perform(
                put("/v1/groups/${group.groupId}")
                    .header(UserIdArgumentResolver.HEADER, "999")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createBody.dropLast(1) + ""","imageAssetId": "46" }"""),
            ).andExpect(status().isOk)

        assertEquals(false, attachMediaUseCase.isAttached(45))
        assertEquals(true, attachMediaUseCase.isAttached(46))
    }

    @Test
    fun `이미지를 지우면 이전 asset이 STAGED로 돌아간다 (M4)`() {
        attachMediaUseCase.issue(48, ownerId = 999)
        attachMediaUseCase.attach(listOf(48))
        val group = seedGroup()
        groupStore.update(group.groupId) { it.copy(imageAssetId = "48") }

        // imageAssetId를 빼고 보낸다 — 대표 이미지를 없애는 편집이다
        mockMvc
            .perform(
                put("/v1/groups/${group.groupId}")
                    .header(UserIdArgumentResolver.HEADER, "999")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createBody),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.imageUrl").doesNotExist())

        // ATTACHED로 남으면 TTL 정리 대상에서 빠져 버킷에 영구히 남는다
        assertEquals(false, attachMediaUseCase.isAttached(48))
    }

    @Test
    fun `이미지를 그대로 둔 편집은 재부착으로 막히지 않는다`() {
        attachMediaUseCase.issue(47, ownerId = 999)
        attachMediaUseCase.attach(listOf(47))
        val group = seedGroup()
        groupStore.update(group.groupId) { it.copy(imageAssetId = "47") }

        mockMvc
            .perform(
                put("/v1/groups/${group.groupId}")
                    .header(UserIdArgumentResolver.HEADER, "999")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createBody.dropLast(1) + ""","imageAssetId": "47" }"""),
            ).andExpect(status().isOk)

        assertEquals(true, attachMediaUseCase.isAttached(47))
    }

    @Test
    fun `없는 그룹은 GROUP_NOT_FOUND다`() {
        mockMvc
            .perform(get("/v1/groups/group_999"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("GROUP_NOT_FOUND"))
    }
}
