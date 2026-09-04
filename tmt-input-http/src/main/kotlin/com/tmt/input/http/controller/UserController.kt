package com.tmt.input.http.controller

import com.tmt.application.port.input.FavoriteKey
import com.tmt.application.port.input.FavoritePlaceView
import com.tmt.application.port.input.GetTicketHistoryUseCase
import com.tmt.application.port.input.GetUserFavoritesUseCase
import com.tmt.application.port.input.GetUserGroupsUseCase
import com.tmt.application.port.input.GetUserProfileUseCase
import com.tmt.application.port.input.GetUserReviewGridUseCase
import com.tmt.application.port.input.GroupCardView
import com.tmt.application.port.input.JoinedGroupKey
import com.tmt.application.port.input.ReviewGridItemView
import com.tmt.application.port.input.ReviewGridKey
import com.tmt.application.port.input.TicketHistoryItemView
import com.tmt.application.port.input.TicketHistoryKey
import com.tmt.common.exception.ErrorCode
import com.tmt.input.http.auth.UserId
import com.tmt.input.http.config.ApiErrorCodes
import com.tmt.input.http.controller.dto.response.CursorPage
import com.tmt.input.http.controller.dto.response.GroupCardResponse
import com.tmt.input.http.controller.dto.response.PlaceCardResponse
import com.tmt.input.http.controller.dto.response.PublicIds
import com.tmt.input.http.controller.paging.CursorCodec
import com.tmt.input.http.controller.paging.CursorCondition
import com.tmt.input.http.controller.paging.CursorSpec
import com.tmt.input.http.controller.paging.PageLimit
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

/**
 * 마이페이지·타인 프로필 실구현 (TMT-274) — UserMockController 10종 중 사용자 9종을 대체한다.
 * 매장 추천(POST /v1/recommendations/places)은 LLM 실구현(TMT-289)까지 mock이 남는다.
 * 두 화면은 탭 3종의 항목 스키마·정렬이 같고, 상단과 소유자 전용 필드(email·티켓·saveId)만 다르다 (J-01 §6-1).
 */
@Tag(name = "마이페이지·타인 프로필", description = "명세 v2 — J · J-01")
@RestController
@RequestMapping("/v1/users")
class UserController(
    private val getUserProfileUseCase: GetUserProfileUseCase,
    private val getUserReviewGridUseCase: GetUserReviewGridUseCase,
    private val getUserGroupsUseCase: GetUserGroupsUseCase,
    private val getUserFavoritesUseCase: GetUserFavoritesUseCase,
    private val getTicketHistoryUseCase: GetTicketHistoryUseCase,
) {
    @Operation(summary = "마이페이지 상단", description = "프로필·티켓 배너·칩 카운트 3종. 칩 숫자는 탭을 열기 전에 보이므로 여기 함께 싣는다 (J §2).")
    @GetMapping("/me")
    fun me(
        @UserId userId: Long,
    ): MyProfileResponse {
        val profile = getUserProfileUseCase.getMine(userId)
        return MyProfileResponse(
            userId = PublicIds.user(profile.userId),
            nickname = profile.nickname,
            email = profile.email,
            profileImageUrl = profile.profileImageUrl,
            availableTicketCount = profile.availableTicketCount ?: 0,
            reviewCount = profile.reviewCount,
            joinedGroupCount = profile.joinedGroupCount,
            favoritePlaceCount = profile.favoritePlaceCount,
        )
    }

    @Operation(
        summary = "내 리뷰 탭",
        description = "2열 사진 그리드라 카드가 아니라 썸네일만 내린다. 사진 0장 리뷰(C4-1)는 thumbnailUrl이 null이다. 미완성 저장은 나오지 않는다 (R8).",
    )
    @ApiErrorCodes(ErrorCode.INVALID_CURSOR)
    @GetMapping("/me/reviews")
    fun myReviews(
        @UserId userId: Long,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(required = false) limit: Int?,
    ): CursorPage<MyReviewGridItem> =
        reviewsPage(userId, cursor, limit) { item ->
            MyReviewGridItem(
                reviewId = PublicIds.review(item.reviewId),
                saveId = PublicIds.save(item.saveId),
                thumbnailUrl = item.thumbnailUrl,
                place = item.toGridPlace(),
                createdAt = item.createdAt.toString(),
            )
        }

    @Operation(summary = "내 그룹 탭", description = "가입 오래된 순 — 홈의 myGroups와 같은 기준이라 두 화면에서 순서가 어긋나지 않는다 (G20).")
    @ApiErrorCodes(ErrorCode.INVALID_CURSOR)
    @GetMapping("/me/groups")
    fun myGroups(
        @UserId userId: Long,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(required = false) limit: Int?,
    ): CursorPage<GroupCardResponse> = groupsPage(ownerId = userId, viewerId = userId, cursor = cursor, limit = limit)

    @Operation(summary = "내 좋아요 탭", description = "찜한 매장 목록 (F1). 이 목록에서 isFavorite은 항상 true다.")
    @ApiErrorCodes(ErrorCode.INVALID_CURSOR)
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
        description =
            "발급·소비·회수 이력과 잔액. 미완성 저장은 목록에 섞이지 않고 inProgressSaveCount 하나로 내린다 — " +
                "0보다 크면 화면이 상단 `작성 중` 배너를 그린다 (T10, J §4-1).",
    )
    @ApiErrorCodes(ErrorCode.INVALID_CURSOR)
    @GetMapping("/me/tickets")
    fun myTickets(
        @UserId userId: Long,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(required = false) limit: Int?,
    ): TicketHistoryResponse {
        val condition = CursorCondition.of(TICKETS_CONDITION, userId)
        val after = CursorCodec.decode(TicketCursorSpec, cursor, condition)
        val pageLimit = PageLimit.of(limit)
        val slice = getTicketHistoryUseCase.list(userId, after, pageLimit)
        val nextCursor =
            slice.items
                .lastOrNull()
                ?.takeIf { slice.hasNext }
                ?.let { CursorCodec.encode(TicketCursorSpec, TicketHistoryKey(it.occurredAt, it.entryId), condition) }
        return TicketHistoryResponse(
            availableCount = slice.availableCount,
            inProgressSaveCount = slice.inProgressSaveCount,
            items = slice.items.map { it.toResponse() },
            nextCursor = nextCursor,
            hasNext = slice.hasNext,
        )
    }

    @Operation(summary = "타인 프로필 상단", description = "email·availableTicketCount는 본인에게만 내린다 (U7). 인증은 선택이다.")
    @ApiErrorCodes(ErrorCode.USER_NOT_FOUND)
    @GetMapping("/{userId}")
    fun userProfile(
        @PathVariable userId: String,
    ): UserProfileResponse {
        val profile = getUserProfileUseCase.getOther(PublicIds.parseUserId(userId))
        return UserProfileResponse(
            userId = PublicIds.user(profile.userId),
            nickname = profile.nickname,
            profileImageUrl = profile.profileImageUrl,
            reviewCount = profile.reviewCount,
            joinedGroupCount = profile.joinedGroupCount,
            favoritePlaceCount = profile.favoritePlaceCount,
        )
    }

    @Operation(summary = "타인 리뷰 탭", description = "saveId는 소유자만 쓰는 핸들이라 빠진다 (S8). 개인 프로필에는 게이트가 없다 (G2).")
    @ApiErrorCodes(ErrorCode.USER_NOT_FOUND, ErrorCode.INVALID_CURSOR)
    @GetMapping("/{userId}/reviews")
    fun userReviews(
        @PathVariable userId: String,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(required = false) limit: Int?,
    ): CursorPage<UserReviewGridItem> =
        reviewsPage(PublicIds.parseUserId(userId), cursor, limit) { item ->
            UserReviewGridItem(
                reviewId = PublicIds.review(item.reviewId),
                thumbnailUrl = item.thumbnailUrl,
                place = item.toGridPlace(),
                createdAt = item.createdAt.toString(),
            )
        }

    @Operation(summary = "타인 그룹 탭", description = "matchedSavedPlaceCount는 조회자 기준이라 비로그인이면 0이다 (§6-1).")
    @ApiErrorCodes(ErrorCode.USER_NOT_FOUND, ErrorCode.INVALID_CURSOR)
    @GetMapping("/{userId}/groups")
    fun userGroups(
        @UserId viewerId: Long?,
        @PathVariable userId: String,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(required = false) limit: Int?,
    ): CursorPage<GroupCardResponse> =
        groupsPage(ownerId = PublicIds.parseUserId(userId), viewerId = viewerId, cursor = cursor, limit = limit)

    @Operation(summary = "타인 좋아요 탭", description = "isFavorite은 조회자 기준이라 여기서는 true가 아닐 수 있다 (F3).")
    @ApiErrorCodes(ErrorCode.USER_NOT_FOUND, ErrorCode.INVALID_CURSOR)
    @GetMapping("/{userId}/favorites")
    fun userFavorites(
        @UserId viewerId: Long?,
        @PathVariable userId: String,
        @RequestParam(required = false) latitude: Double?,
        @RequestParam(required = false) longitude: Double?,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(required = false) limit: Int?,
    ): CursorPage<PlaceCardResponse> =
        favoritesPage(PublicIds.parseUserId(userId), viewerId, latitude, longitude, cursor, limit)

    private fun <T> reviewsPage(
        ownerId: Long,
        cursor: String?,
        limit: Int?,
        toItem: (ReviewGridItemView) -> T,
    ): CursorPage<T> {
        val condition = CursorCondition.of(REVIEWS_CONDITION, ownerId)
        val after = CursorCodec.decode(ReviewGridCursorSpec, cursor, condition)
        val pageLimit = PageLimit.of(limit)
        val slice = getUserReviewGridUseCase.list(ownerId, after, pageLimit)
        val nextCursor =
            slice.items
                .lastOrNull()
                ?.takeIf { slice.hasNext }
                ?.let { CursorCodec.encode(ReviewGridCursorSpec, ReviewGridKey(it.createdAt, it.reviewId), condition) }
        return CursorPage(items = slice.items.map(toItem), nextCursor = nextCursor, hasNext = slice.hasNext)
    }

    private fun groupsPage(
        ownerId: Long,
        viewerId: Long?,
        cursor: String?,
        limit: Int?,
    ): CursorPage<GroupCardResponse> {
        val condition = CursorCondition.of(GROUPS_CONDITION, ownerId)
        val after = CursorCodec.decode(JoinedGroupCursorSpec, cursor, condition)
        val pageLimit = PageLimit.of(limit)
        val slice = getUserGroupsUseCase.list(ownerId, viewerId, after, pageLimit)
        val nextCursor =
            slice.items
                .lastOrNull()
                ?.takeIf { slice.hasNext }
                ?.let {
                    CursorCodec.encode(
                        JoinedGroupCursorSpec,
                        JoinedGroupKey(it.joinedAt, it.card.groupId),
                        condition,
                    )
                }
        return CursorPage(
            items = slice.items.map { it.card.toResponse() },
            nextCursor = nextCursor,
            hasNext = slice.hasNext,
        )
    }

    private fun favoritesPage(
        ownerId: Long,
        viewerId: Long?,
        latitude: Double?,
        longitude: Double?,
        cursor: String?,
        limit: Int?,
    ): CursorPage<PlaceCardResponse> {
        // 좌표는 정렬에 영향이 없어 조건 해시에 넣지 않는다 — 위치가 바뀌어도 커서가 유지된다
        val condition = CursorCondition.of(FAVORITES_CONDITION, ownerId)
        val after = CursorCodec.decode(FavoriteCursorSpec, cursor, condition)
        val pageLimit = PageLimit.of(limit)
        val slice = getUserFavoritesUseCase.list(ownerId, viewerId, latitude, longitude, after, pageLimit)
        val nextCursor =
            slice.items
                .lastOrNull()
                ?.takeIf { slice.hasNext }
                ?.let { CursorCodec.encode(FavoriteCursorSpec, FavoriteKey(it.favoritedAt, it.placeId), condition) }
        return CursorPage(items = slice.items.map { it.toResponse() }, nextCursor = nextCursor, hasNext = slice.hasNext)
    }

    private fun ReviewGridItemView.toGridPlace() =
        ReviewGridPlace(
            placeId = PublicIds.place(placeId),
            name = placeName,
            categoryName = placeCategoryName,
        )

    private fun GroupCardView.toResponse() =
        GroupCardResponse(
            groupId = PublicIds.group(groupId),
            name = name,
            oneLineDescription = oneLineDescription,
            coverImageUrl = coverImageUrl,
            memberCount = memberCount,
            reviewCount = reviewCount,
            placeCount = placeCount,
            matchedSavedPlaceCount = matchedSavedPlaceCount,
        )

    private fun FavoritePlaceView.toResponse() =
        PlaceCardResponse(
            placeId = PublicIds.place(placeId),
            name = name,
            roadAddress = roadAddress,
            regionName = regionName,
            categoryId = categoryId,
            categoryName = categoryName,
            averageRating = averageRating,
            reviewCount = reviewCount,
            thumbnailUrl = thumbnailUrl,
            distanceMeters = distanceMeters,
            isFavorite = isFavorite,
        )

    private fun TicketHistoryItemView.toResponse() =
        TicketHistoryItem(
            entryId = entryId,
            type = TicketHistoryType.valueOf(type.name),
            amount = amount,
            saveId = saveId?.let(PublicIds::save),
            place = place?.let { TicketHistoryItem.PlaceRef(PublicIds.place(it.placeId), it.name, it.roadAddress) },
            group = group?.let { TicketHistoryItem.GroupRef(PublicIds.group(it.groupId), it.name) },
            occurredAt = occurredAt.toString(),
        )

    data class MyProfileResponse(
        val userId: String,
        val nickname: String,
        /** users에 이메일이 없다 — 동의항목을 수집하지 않아 당분간 항상 null (J §2). */
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
        /** 첫 사진. 사진 0장 리뷰(C4-1)는 null — 화면이 빈 썸네일을 그린다 (J §8-3) */
        val thumbnailUrl: String?,
        val place: ReviewGridPlace,
        val createdAt: String,
    )

    /** 타인 항목에는 saveId가 없다 — 소유자만 쓰는 핸들이다 (S8). */
    data class UserReviewGridItem(
        val reviewId: String,
        val thumbnailUrl: String?,
        val place: ReviewGridPlace,
        val createdAt: String,
    )

    data class ReviewGridPlace(
        val placeId: String,
        val name: String,
        /** 매장 추천 진입 격자가 아이콘을 고르는 데 쓴다 (J §5-1). 매핑 실패 시 null */
        val categoryName: String?,
    )

    data class TicketHistoryResponse(
        val availableCount: Int,
        /** 아직 리뷰가 되지 않은 내 저장의 수. 커서와 무관한 전체 건수 — 0보다 크면 상단 배너 (J §4-1) */
        val inProgressSaveCount: Int,
        val items: List<TicketHistoryItem>,
        val nextCursor: String?,
        val hasNext: Boolean,
    )

    /** 이력 행의 종류 (J §4-1). 발급·소비·회수 4종 — 미완성 저장은 inProgressSaveCount로 갔다 (2026-09-03 개정). */
    enum class TicketHistoryType {
        SIGNUP_REWARD,
        REVIEW_REWARD,
        REVIEW_DELETE_REVOKE,
        GROUP_JOIN,
    }

    data class TicketHistoryItem(
        val entryId: String,
        val type: TicketHistoryType,
        /** 항상 +1 또는 -1. 화면 제목(`티켓 획득`·`티켓 사용`)은 이 부호에서 파생한다 */
        val amount: Int,
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

    internal object ReviewGridCursorSpec : CursorSpec<ReviewGridKey> {
        override fun toKeys(key: ReviewGridKey) = listOf(key.createdAt.toString(), key.reviewId.toString())

        override fun fromKeys(keys: List<String>): ReviewGridKey {
            require(keys.size == 2) { "정렬 키 2개가 필요하다" }
            return ReviewGridKey(Instant.parse(keys[0]), keys[1].toLong())
        }
    }

    internal object JoinedGroupCursorSpec : CursorSpec<JoinedGroupKey> {
        override fun toKeys(key: JoinedGroupKey) = listOf(key.joinedAt.toString(), key.groupId.toString())

        override fun fromKeys(keys: List<String>): JoinedGroupKey {
            require(keys.size == 2) { "정렬 키 2개가 필요하다" }
            return JoinedGroupKey(Instant.parse(keys[0]), keys[1].toLong())
        }
    }

    internal object FavoriteCursorSpec : CursorSpec<FavoriteKey> {
        override fun toKeys(key: FavoriteKey) = listOf(key.favoritedAt.toString(), key.placeId.toString())

        override fun fromKeys(keys: List<String>): FavoriteKey {
            require(keys.size == 2) { "정렬 키 2개가 필요하다" }
            return FavoriteKey(Instant.parse(keys[0]), keys[1].toLong())
        }
    }

    internal object TicketCursorSpec : CursorSpec<TicketHistoryKey> {
        override fun toKeys(key: TicketHistoryKey) = listOf(key.occurredAt.toString(), key.entryId)

        override fun fromKeys(keys: List<String>): TicketHistoryKey {
            require(keys.size == 2) { "정렬 키 2개가 필요하다" }
            return TicketHistoryKey(Instant.parse(keys[0]), keys[1])
        }
    }

    companion object {
        private const val REVIEWS_CONDITION = "USER_REVIEWS"
        private const val GROUPS_CONDITION = "USER_GROUPS"
        private const val FAVORITES_CONDITION = "USER_FAVORITES"
        private const val TICKETS_CONDITION = "USER_TICKETS"
    }
}
