package com.tmt.input.http.mock

import com.tmt.input.http.auth.UserIdArgumentResolver
import com.tmt.input.http.exception.ExceptionAdvice
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Instant

class UserMockControllerTest {
    private val placeStore = InMemoryStore<MockPlace>(idPrefix = "place")
    private val saveStore = InMemoryStore<MockSave>(idPrefix = "save")
    private val groupStore = InMemoryStore<MockGroup>(idPrefix = "group")
    private val membershipStore = MockMembershipStore()
    private val shareStore = MockReviewShareStore()
    private val favoriteStore = MockFavoriteStore()
    private val ticketLedger = MockTicketLedger()
    private val userStore =
        MockUserStore(
            listOf(
                MockUser(1, "조용한 미식가", "tester1@example.com"),
                MockUser(7, "면요리 연구가", "tester7@example.com"),
            ),
        )

    private val mockMvc: MockMvc =
        MockMvcBuilders
            .standaloneSetup(
                UserMockController(
                    userStore,
                    saveStore,
                    placeStore,
                    groupStore,
                    membershipStore,
                    favoriteStore,
                    ticketLedger,
                    GroupAssembler(saveStore, membershipStore, shareStore),
                    PlaceCardAssembler(saveStore, favoriteStore),
                ),
            ).setCustomArgumentResolvers(UserIdArgumentResolver())
            .setControllerAdvice(ExceptionAdvice())
            .build()

    private fun seedReview(
        ownerId: Long,
        placeId: String,
        reviewId: String,
        createdAt: Instant = Instant.parse("2026-08-12T09:11:03.412Z"),
    ): MockSave =
        saveStore.create { id ->
            MockSave(
                saveId = id,
                ownerId = ownerId,
                placeId = placeId,
                photoAssetIds = listOf("asset_1"),
                companionTagIds = emptyList(),
                positivePointTagIds = emptyList(),
                rating = 5,
                content = "맛있어요",
                reviewId = reviewId,
                createdAt = createdAt,
                updatedAt = createdAt,
            )
        }

    @Test
    fun `마이페이지 상단은 칩 카운트 3종과 티켓 장수를 함께 내린다 (J 2)`() {
        val place = MockFixtures.place(placeStore, "델리스피자")
        seedReview(ownerId = 1, placeId = place.placeId, reviewId = "review_1")
        // 미완성 저장은 리뷰 수에 안 들어간다 (R8)
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
        val group = groupStore.create { id -> MockFixtures.group(id, ownerId = 1) }
        membershipStore.join(group.groupId, 1, Instant.now())
        favoriteStore.add(1, place.placeId)

        mockMvc
            .perform(get("/v1/users/me").header(UserIdArgumentResolver.HEADER, "1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.userId").value("user_1"))
            .andExpect(jsonPath("$.nickname").value("조용한 미식가"))
            .andExpect(jsonPath("$.email").value("tester1@example.com"))
            .andExpect(jsonPath("$.availableTicketCount").value(1))
            .andExpect(jsonPath("$.reviewCount").value(1))
            .andExpect(jsonPath("$.joinedGroupCount").value(1))
            .andExpect(jsonPath("$.favoritePlaceCount").value(1))
    }

    @Test
    fun `마이페이지는 인증이 필수다 (U6)`() {
        mockMvc.perform(get("/v1/users/me")).andExpect(status().isUnauthorized)
        mockMvc.perform(get("/v1/users/me/tickets")).andExpect(status().isUnauthorized)
    }

    @Test
    fun `내 리뷰 탭은 썸네일 그리드이고 미완성 저장은 빠진다 (R8)`() {
        val place = MockFixtures.place(placeStore, "델리스피자")
        seedReview(ownerId = 1, placeId = place.placeId, reviewId = "review_1")
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
            .perform(get("/v1/users/me/reviews").header(UserIdArgumentResolver.HEADER, "1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].saveId").isNotEmpty)
            .andExpect(jsonPath("$.items[0].thumbnailUrl").isNotEmpty)
            .andExpect(jsonPath("$.items[0].place.categoryName").value("양식"))
    }

    @Test
    fun `타인 리뷰 탭에는 saveId가 없다 (S8)`() {
        val place = MockFixtures.place(placeStore, "델리스피자")
        seedReview(ownerId = 7, placeId = place.placeId, reviewId = "review_1")

        mockMvc
            .perform(get("/v1/users/7/reviews"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].reviewId").value("review_1"))
            .andExpect(jsonPath("$.items[0].saveId").doesNotExist())
    }

    @Test
    fun `타인 프로필에는 이메일과 티켓 장수가 없다 (U7)`() {
        mockMvc
            .perform(get("/v1/users/7"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.nickname").value("면요리 연구가"))
            .andExpect(jsonPath("$.email").doesNotExist())
            .andExpect(jsonPath("$.availableTicketCount").doesNotExist())
    }

    @Test
    fun `응답의 userId 표기를 그대로 경로에 써도 열린다`() {
        // 카드·프로필이 user_7로 내려주므로 FE가 그 값을 그대로 경로에 넣는다
        mockMvc
            .perform(get("/v1/users/user_7"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.userId").value("user_7"))
            .andExpect(jsonPath("$.nickname").value("면요리 연구가"))
    }

    @Test
    fun `없는 사용자는 404다 (J-01 6-2)`() {
        mockMvc.perform(get("/v1/users/404")).andExpect(status().isNotFound)
        // 숫자로 해석되지 않는 값도 500이 아니라 404다
        mockMvc.perform(get("/v1/users/ghost")).andExpect(status().isNotFound)
        mockMvc.perform(get("/v1/users/404/reviews")).andExpect(status().isNotFound)
        mockMvc.perform(get("/v1/users/404/groups")).andExpect(status().isNotFound)
        mockMvc.perform(get("/v1/users/404/favorites")).andExpect(status().isNotFound)
    }

    @Test
    fun `타인 프로필은 비로그인으로 열리고 조회자 기준 필드는 0 false다 (U2)`() {
        val place = MockFixtures.place(placeStore, "델리스피자")
        seedReview(ownerId = 7, placeId = place.placeId, reviewId = "review_1")
        favoriteStore.add(7, place.placeId)
        val group = groupStore.create { id -> MockFixtures.group(id, ownerId = 7) }
        membershipStore.join(group.groupId, 7, Instant.now())

        mockMvc
            .perform(get("/v1/users/7/favorites"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items[0].isFavorite").value(false))

        mockMvc
            .perform(get("/v1/users/7/groups"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items[0].matchedSavedPlaceCount").value(0))
    }

    @Test
    fun `내 그룹 탭은 가입 오래된 순이다 (G20)`() {
        val first = groupStore.create { id -> MockFixtures.group(id, name = "먼저 가입", ownerId = 9) }
        val second = groupStore.create { id -> MockFixtures.group(id, name = "나중 가입", ownerId = 9) }
        membershipStore.join(second.groupId, 1, Instant.parse("2026-08-20T00:00:00Z"))
        membershipStore.join(first.groupId, 1, Instant.parse("2026-08-10T00:00:00Z"))

        mockMvc
            .perform(get("/v1/users/me/groups").header(UserIdArgumentResolver.HEADER, "1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items[0].name").value("먼저 가입"))
            .andExpect(jsonPath("$.items[1].name").value("나중 가입"))
    }

    @Test
    fun `좋아요 탭은 찜한 최신순이고 내 목록에서는 항상 true다 (J 3-3)`() {
        val old = MockFixtures.place(placeStore, "델리스피자")
        val recent = MockFixtures.place(placeStore, "오즈 커피")
        favoriteStore.add(1, old.placeId, Instant.parse("2026-08-10T00:00:00Z"))
        favoriteStore.add(1, recent.placeId, Instant.parse("2026-08-20T00:00:00Z"))

        mockMvc
            .perform(get("/v1/users/me/favorites").header(UserIdArgumentResolver.HEADER, "1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items[0].name").value("오즈 커피"))
            .andExpect(jsonPath("$.items[0].isFavorite").value(true))
            .andExpect(jsonPath("$.items[1].name").value("델리스피자"))
    }

    @Test
    fun `티켓 이력은 발급 소비와 작성 중 행이 한 목록이다 (T10)`() {
        val place = MockFixtures.place(placeStore, "한판승부")
        val group = groupStore.create { id -> MockFixtures.group(id, name = "성수 커피 탐험대", ownerId = 9) }
        val save = seedReview(ownerId = 1, placeId = place.placeId, reviewId = "review_1")
        ticketLedger.tryGrant(1, save.saveId, place.placeId)
        ticketLedger.tryConsume(1, TicketEntryType.GROUP_JOIN, groupId = group.groupId)
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
            .perform(get("/v1/users/me/tickets").header(UserIdArgumentResolver.HEADER, "1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.availableCount").value(1))
            .andExpect(jsonPath("$.items.length()").value(4))
            // 최신순 — 방금 만든 미완성 저장이 맨 위다
            .andExpect(jsonPath("$.items[0].type").value("SAVE_IN_PROGRESS"))
            .andExpect(jsonPath("$.items[0].amount").doesNotExist())
            .andExpect(jsonPath("$.items[0].saveId").isNotEmpty)
            .andExpect(jsonPath("$.items[1].type").value("GROUP_JOIN"))
            .andExpect(jsonPath("$.items[1].amount").value(-1))
            .andExpect(jsonPath("$.items[1].group.name").value("성수 커피 탐험대"))
            .andExpect(jsonPath("$.items[2].type").value("REVIEW_REWARD"))
            .andExpect(jsonPath("$.items[2].amount").value(1))
            .andExpect(jsonPath("$.items[2].place.name").value("한판승부"))
            // 가입 보상은 매장도 그룹도 없다 (T11)
            .andExpect(jsonPath("$.items[3].type").value("SIGNUP_REWARD"))
            .andExpect(jsonPath("$.items[3].place").doesNotExist())
            .andExpect(jsonPath("$.items[3].group").doesNotExist())
    }
}
