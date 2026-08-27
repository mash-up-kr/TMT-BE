package com.tmt.input.http.mock

import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException
import com.tmt.input.http.auth.UserId
import com.tmt.input.http.config.ApiErrorCodes
import com.tmt.input.http.controller.dto.response.CursorPage
import com.tmt.input.http.controller.dto.response.GroupCardResponse
import com.tmt.input.http.controller.dto.response.PlaceCardResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 마이페이지 (명세 v2 J) · 타인 프로필 (J-01). 두 화면은 탭 3종의 항목 스키마·정렬이 같고,
 * 상단 영역과 소유자 전용 필드(email·티켓·saveId)만 다르다 (J-01 §6-1).
 */
@Tag(name = "마이페이지·타인 프로필 (mock)", description = "명세 v2 — J · J-01")
@RestController
@RequestMapping("/v1/users")
class UserMockController(
    private val mockUserStore: MockUserStore,
    private val mockSaveStore: InMemoryStore<MockSave>,
    private val mockPlaceStore: InMemoryStore<MockPlace>,
    private val mockGroupStore: InMemoryStore<MockGroup>,
    private val mockMembershipStore: MockMembershipStore,
    private val mockFavoriteStore: MockFavoriteStore,
    private val mockTicketLedger: MockTicketLedger,
    private val groupAssembler: GroupAssembler,
    private val placeCardAssembler: PlaceCardAssembler,
) {
    @Operation(summary = "마이페이지 상단", description = "프로필·티켓 배너·칩 카운트 3종. 칩 숫자는 탭을 열기 전에 보이므로 여기 함께 싣는다 (J §2).")
    @GetMapping("/me")
    fun me(
        @UserId userId: Long,
    ): MyProfileResponse {
        val user = mockUserStore.authorOf(userId)
        return MyProfileResponse(
            userId = "user_$userId",
            nickname = user.nickname,
            email = user.email,
            profileImageUrl = user.profileImageUrl,
            availableTicketCount = mockTicketLedger.availableCount(userId),
            reviewCount = reviewsOf(user.userId).size,
            joinedGroupCount = mockMembershipStore.joinedGroups(user.userId).size,
            favoritePlaceCount = mockFavoriteStore.count(user.userId),
        )
    }

    @Operation(summary = "내 리뷰 탭", description = "2열 사진 그리드라 카드가 아니라 썸네일만 내린다. 미완성 저장은 나오지 않는다 (R8).")
    @GetMapping("/me/reviews")
    fun myReviews(
        @UserId userId: Long,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(required = false) limit: Int?,
    ): CursorPage<MyReviewGridItem> =
        MockCursor.paginate(reviewsOf(userId), cursor, limit) { save ->
            MyReviewGridItem(
                reviewId = save.reviewId!!,
                saveId = save.saveId,
                thumbnailUrl = mockMediaUrl(save.photoAssetIds.first()),
                place = placeSummaryOf(save.placeId),
                createdAt = save.createdAt.toString(),
            )
        }

    @Operation(summary = "내 그룹 탭", description = "가입 오래된 순 — 홈의 myGroups와 같은 기준이라 두 화면에서 순서가 어긋나지 않는다 (G20).")
    @GetMapping("/me/groups")
    fun myGroups(
        @UserId userId: Long,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(required = false) limit: Int?,
    ): CursorPage<GroupCardResponse> = groupsPage(ownerId = userId, viewerId = userId, cursor = cursor, limit = limit)

    @Operation(summary = "내 좋아요 탭", description = "찜한 매장 목록 (F1). 이 목록에서 isFavorite은 항상 true다.")
    @GetMapping("/me/favorites")
    fun myFavorites(
        @UserId userId: Long,
        @RequestParam(required = false) latitude: Double?,
        @RequestParam(required = false) longitude: Double?,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(required = false) limit: Int?,
    ): CursorPage<PlaceCardResponse> = favoritesPage(userId, userId, latitude, longitude, cursor, limit)

    @Operation(
        summary = "내 티켓",
        description = "발급·소비·회수와 미완성 저장이 한 목록이다. amount가 null이면 화면이 `작성 중` 배지를 그린다 (T10).",
    )
    @GetMapping("/me/tickets")
    fun myTickets(
        @UserId userId: Long,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(required = false) limit: Int?,
    ): TicketHistoryResponse {
        val recorded =
            mockTicketLedger.historyOf(userId).map { entry ->
                SortableEntry(
                    occurredAt = entry.occurredAt,
                    seq = entrySeq(entry.entryId),
                    item =
                        TicketHistoryItem(
                            entryId = entry.entryId,
                            type = TicketHistoryType.of(entry.type),
                            amount = entry.amount,
                            saveId = entry.saveId,
                            place = entry.placeId?.let { placeRefOf(it) },
                            group = entry.groupId?.let { groupRefOf(it) },
                            occurredAt = entry.occurredAt.toString(),
                        ),
                )
            }
        // 아직 티켓이 나가지 않은 저장도 같은 목록에 섞인다 — 목록을 나누면 클라이언트가 두 커서를 병합해야 한다
        val inProgress =
            mockSaveStore
                .findAll()
                .filter { it.ownerId == userId && it.reviewId == null }
                .map { save ->
                    SortableEntry(
                        occurredAt = save.updatedAt,
                        seq = entrySeq(save.saveId),
                        item =
                            TicketHistoryItem(
                                entryId = "tkh_save_${save.saveId}",
                                type = TicketHistoryType.SAVE_IN_PROGRESS,
                                amount = null,
                                saveId = save.saveId,
                                place = placeRefOf(save.placeId),
                                group = null,
                                occurredAt = save.updatedAt.toString(),
                            ),
                    )
                }

        // 시각은 Instant로, tie-breaker는 id의 숫자로 비교한다 —
        // 문자열로 비교하면 소수 자리가 있는 시각과 tkh_9 · tkh_10의 순서가 뒤집힌다
        val items =
            (recorded + inProgress)
                .sortedWith(compareByDescending<SortableEntry> { it.occurredAt }.thenByDescending { it.seq })
                .map { it.item }
        val page = MockCursor.paginate(items, cursor, limit) { it }
        return TicketHistoryResponse(
            availableCount = mockTicketLedger.availableCount(userId),
            items = page.items,
            nextCursor = page.nextCursor,
            hasNext = page.hasNext,
        )
    }

    @Operation(summary = "타인 프로필 상단", description = "email·availableTicketCount는 본인에게만 내린다 (U7). 인증은 선택이다.")
    @ApiErrorCodes(ErrorCode.USER_NOT_FOUND)
    @GetMapping("/{userId}")
    fun userProfile(
        @PathVariable userId: String,
    ): UserProfileResponse {
        val user = findUser(userId)
        return UserProfileResponse(
            userId = "user_${user.userId}",
            nickname = user.nickname,
            profileImageUrl = user.profileImageUrl,
            reviewCount = reviewsOf(user.userId).size,
            joinedGroupCount = mockMembershipStore.joinedGroups(user.userId).size,
            favoritePlaceCount = mockFavoriteStore.count(user.userId),
        )
    }

    @Operation(summary = "타인 리뷰 탭", description = "saveId는 소유자만 쓰는 핸들이라 빠진다 (S8). 개인 프로필에는 게이트가 없다 (G2).")
    @ApiErrorCodes(ErrorCode.USER_NOT_FOUND)
    @GetMapping("/{userId}/reviews")
    fun userReviews(
        @PathVariable userId: String,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(required = false) limit: Int?,
    ): CursorPage<UserReviewGridItem> {
        val owner = findUser(userId).userId
        return MockCursor.paginate(reviewsOf(owner), cursor, limit) { save ->
            UserReviewGridItem(
                reviewId = save.reviewId!!,
                thumbnailUrl = mockMediaUrl(save.photoAssetIds.first()),
                place = placeSummaryOf(save.placeId),
                createdAt = save.createdAt.toString(),
            )
        }
    }

    @Operation(summary = "타인 그룹 탭", description = "matchedSavedPlaceCount는 조회자 기준이라 비로그인이면 0이다 (§6-1).")
    @ApiErrorCodes(ErrorCode.USER_NOT_FOUND)
    @GetMapping("/{userId}/groups")
    fun userGroups(
        @UserId viewerId: Long?,
        @PathVariable userId: String,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(required = false) limit: Int?,
    ): CursorPage<GroupCardResponse> {
        val owner = findUser(userId).userId
        return groupsPage(ownerId = owner, viewerId = viewerId, cursor = cursor, limit = limit)
    }

    @Operation(summary = "타인 좋아요 탭", description = "isFavorite은 조회자 기준이라 여기서는 true가 아닐 수 있다 (F3).")
    @ApiErrorCodes(ErrorCode.USER_NOT_FOUND)
    @GetMapping("/{userId}/favorites")
    fun userFavorites(
        @UserId viewerId: Long?,
        @PathVariable userId: String,
        @RequestParam(required = false) latitude: Double?,
        @RequestParam(required = false) longitude: Double?,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(required = false) limit: Int?,
    ): CursorPage<PlaceCardResponse> {
        val owner = findUser(userId).userId
        return favoritesPage(owner, viewerId, latitude, longitude, cursor, limit)
    }

    /** `tkh_12`·`save_7`의 끝 숫자. 정렬 tie-breaker라 해석되지 않으면 맨 뒤로 보낸다. */
    private fun entrySeq(id: String): Long = id.substringAfterLast('_').toLongOrNull() ?: 0

    /**
     * 경로의 userId는 응답 표기(`user_7`)와 숫자(`7`) 둘 다 받는다 —
     * FE가 카드에서 받은 `author.userId`를 그대로 경로에 넣기 때문이다.
     */
    private fun findUser(userId: String): MockUser {
        val id = userId.removePrefix(USER_ID_PREFIX).toLongOrNull() ?: throw TmtException(ErrorCode.USER_NOT_FOUND)
        return mockUserStore.find(id) ?: throw TmtException(ErrorCode.USER_NOT_FOUND)
    }

    /** 완성된 리뷰만, 최신순 (R8). 칩 카운트와 리뷰 탭이 같은 집합을 본다. */
    private fun reviewsOf(userId: Long): List<MockSave> =
        mockSaveStore
            .findAll()
            .filter { it.ownerId == userId && it.reviewId != null }
            .sortedWith(compareByDescending<MockSave> { it.createdAt }.thenByDescending { it.reviewId })

    private fun groupsPage(
        ownerId: Long,
        viewerId: Long?,
        cursor: String?,
        limit: Int?,
    ): CursorPage<GroupCardResponse> {
        val groups =
            mockMembershipStore
                .joinedGroups(ownerId)
                .sortedWith(compareBy({ (_, joinedAt) -> joinedAt }, { (groupId, _) -> groupId }))
                .mapNotNull { (groupId, _) -> mockGroupStore.findById(groupId) }
        return MockCursor.paginate(groups, cursor, limit) { groupAssembler.card(it, viewerId) }
    }

    private fun favoritesPage(
        ownerId: Long,
        viewerId: Long?,
        latitude: Double?,
        longitude: Double?,
        cursor: String?,
        limit: Int?,
    ): CursorPage<PlaceCardResponse> {
        val places = mockFavoriteStore.favoritePlaceIds(ownerId).mapNotNull { mockPlaceStore.findById(it) }
        return MockCursor.paginate(places, cursor, limit) {
            placeCardAssembler.assemble(it, viewerId, latitude, longitude)
        }
    }

    private fun placeSummaryOf(placeId: String): ReviewGridPlace {
        val place = mockPlaceStore.findById(placeId)
        return ReviewGridPlace(
            placeId = placeId,
            name = place?.name ?: "(삭제된 매장)",
            categoryName = place?.categoryName,
        )
    }

    private fun placeRefOf(placeId: String): TicketHistoryItem.PlaceRef? =
        mockPlaceStore.findById(placeId)?.let {
            TicketHistoryItem.PlaceRef(placeId = it.placeId, name = it.name, roadAddress = it.roadAddress)
        }

    private fun groupRefOf(groupId: String): TicketHistoryItem.GroupRef? =
        mockGroupStore.findById(groupId)?.let { TicketHistoryItem.GroupRef(groupId = it.groupId, name = it.name) }

    data class MyProfileResponse(
        val userId: String,
        val nickname: String,
        /** 카카오 동의 항목을 못 받으면 null (J §2). */
        val email: String?,
        val profileImageUrl: String?,
        val availableTicketCount: Int,
        val reviewCount: Int,
        val joinedGroupCount: Int,
        val favoritePlaceCount: Int,
    )

    /** 타인 프로필에는 email·availableTicketCount가 없다 (U7). */
    data class UserProfileResponse(
        val userId: String,
        val nickname: String,
        val profileImageUrl: String?,
        val reviewCount: Int,
        val joinedGroupCount: Int,
        val favoritePlaceCount: Int,
    )

    data class MyReviewGridItem(
        val reviewId: String,
        val saveId: String,
        val thumbnailUrl: String,
        val place: ReviewGridPlace,
        val createdAt: String,
    )

    /** 타인 항목에는 saveId가 없다 — 소유자만 쓰는 핸들이다 (S8). */
    data class UserReviewGridItem(
        val reviewId: String,
        val thumbnailUrl: String,
        val place: ReviewGridPlace,
        val createdAt: String,
    )

    data class ReviewGridPlace(
        val placeId: String,
        val name: String,
        /** 매장 추천 진입 격자가 아이콘을 고르는 데 쓴다 (J §5-1). 매핑 실패 시 null */
        val categoryName: String?,
    )

    private data class SortableEntry(
        val occurredAt: java.time.Instant,
        val seq: Long,
        val item: TicketHistoryItem,
    )

    data class TicketHistoryResponse(
        val availableCount: Int,
        val items: List<TicketHistoryItem>,
        val nextCursor: String?,
        val hasNext: Boolean,
    )

    /** 이력 행의 종류 (J §4-1). 저장에서 파생하는 SAVE_IN_PROGRESS가 [TicketEntryType]보다 하나 많다. */
    enum class TicketHistoryType {
        SAVE_IN_PROGRESS,
        SIGNUP_REWARD,
        REVIEW_REWARD,
        REVIEW_DELETE_REVOKE,
        GROUP_JOIN,
        ;

        companion object {
            fun of(type: TicketEntryType): TicketHistoryType = valueOf(type.name)
        }
    }

    data class TicketHistoryItem(
        val entryId: String,
        val type: TicketHistoryType,
        /** null이면 티켓이 오간 적 없는 행이다 — 화면이 `작성 중` 배지를 그린다 (T10). */
        val amount: Int?,
        val saveId: String?,
        /** 매장과 무관한 이력이면 null. SIGNUP_REWARD는 place·group이 둘 다 null이다 (T11). */
        val place: PlaceRef?,
        val group: GroupRef?,
        val occurredAt: String,
    ) {
        data class PlaceRef(
            val placeId: String,
            val name: String,
            val roadAddress: String,
        )

        data class GroupRef(
            val groupId: String,
            val name: String,
        )
    }

    companion object {
        private const val USER_ID_PREFIX = "user_"
    }
}
