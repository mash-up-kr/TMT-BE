package com.tmt.input.http.mock

import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException
import com.tmt.input.http.auth.UserId
import com.tmt.input.http.config.ApiErrorCodes
import com.tmt.input.http.idempotency.IdempotencyKey
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.time.Instant

@Tag(name = "그룹 가입·리뷰 공유 (mock)", description = "명세 v2 — H. 그룹 게시(공유)")
@RestController
@RequestMapping("/v1/groups/{groupId}")
class GroupMembershipMockController(
    private val mockGroupStore: InMemoryStore<MockGroup>,
    private val mockSaveStore: InMemoryStore<MockSave>,
    private val mockPlaceStore: InMemoryStore<MockPlace>,
    private val mockMembershipStore: MockMembershipStore,
    private val mockReviewShareStore: MockReviewShareStore,
    private val mockTicketLedger: MockTicketLedger,
    private val mockIdempotencyRegistry: MockIdempotencyRegistry,
) {
    @Operation(summary = "가입 팝업 정보", description = "티켓 보유·부족 분기. preview는 참고값이고 가입이 조건을 다시 검증한다 (TX-3).")
    @ApiErrorCodes(ErrorCode.GROUP_NOT_FOUND)
    @GetMapping("/join-preview")
    fun joinPreview(
        @UserId userId: Long,
        @PathVariable groupId: String,
    ): JoinPreviewResponse {
        val group = findGroup(groupId)
        val available = mockTicketLedger.availableCount(userId)
        val blockedReason =
            when {
                mockMembershipStore.isMember(groupId, userId) -> "ALREADY_MEMBER"
                available < REQUIRED_TICKETS -> "TICKET_REQUIRED"
                else -> null
            }
        return JoinPreviewResponse(
            group =
                JoinPreviewResponse.GroupSummary(
                    groupId = group.groupId,
                    name = group.name,
                    imageUrl = group.imageAssetId?.let(::mockMediaUrl),
                ),
            availableTicketCount = available,
            requiredTicketCount = REQUIRED_TICKETS,
            joinable = blockedReason == null,
            blockedReason = blockedReason,
        )
    }

    @Operation(summary = "가입", description = "티켓 1장을 소비해 가입한다. 티켓 소비·멤버십 생성·자동 공유가 한 트랜잭션이다 (TX-3).")
    @ApiErrorCodes(
        ErrorCode.GROUP_NOT_FOUND,
        ErrorCode.REVIEW_NOT_FOUND,
        ErrorCode.ALREADY_GROUP_MEMBER,
        ErrorCode.GROUP_JOIN_TICKET_REQUIRED,
    )
    @PostMapping("/memberships")
    @ResponseStatus(HttpStatus.CREATED)
    fun join(
        @UserId userId: Long,
        @PathVariable groupId: String,
        @IdempotencyKey key: String,
        @RequestBody(required = false) requestBody: JoinRequest?,
    ): ResponseEntity<JoinResponse> {
        val request = requestBody ?: JoinRequest()

        // 재현 검사가 isMember 가드보다 앞에 있어야 한다 — 뒤로 가면 성공한 가입의 재시도가
        // 409 ALREADY_GROUP_MEMBER를 받고, FE는 sourceReviewId가 공유됐는지 알 수 없다 (규약 §9)
        val endpoint = "POST /v1/groups/$groupId/memberships"
        mockIdempotencyRegistry.find(userId, endpoint, key)?.let { entry ->
            if (entry.bodyFingerprint != request.toString()) {
                throw TmtException(ErrorCode.IDEMPOTENCY_CONFLICT)
            }
            val replayed = entry.response as JoinResponse
            return ResponseEntity.created(URI.create("/v1/groups/$groupId/memberships/me")).body(replayed)
        }

        findGroup(groupId)

        // 이미 가입은 티켓 부족보다 먼저 판정한다 (G8)
        if (mockMembershipStore.isMember(groupId, userId)) {
            throw TmtException(ErrorCode.ALREADY_GROUP_MEMBER)
        }

        // 단수(deprecated)와 복수를 합집합으로 받는다 (TMT-241) — 전건이 내 리뷰여야 공유가 시작된다
        val sourceReviewIds = (listOfNotNull(request.sourceReviewId) + request.sourceReviewIds.orEmpty()).distinct()
        val myReviewIds =
            mockSaveStore
                .findAll()
                .filter { it.ownerId == userId }
                .mapNotNull { it.reviewId }
                .toSet()
        if (sourceReviewIds.any { it !in myReviewIds }) {
            throw TmtException(ErrorCode.REVIEW_NOT_FOUND)
        }

        if (!mockTicketLedger.tryConsume(userId, TicketEntryType.GROUP_JOIN, groupId = groupId)) {
            throw GroupJoinTicketRequiredException(availableCount = mockTicketLedger.availableCount(userId))
        }

        val joinedAt = Instant.now()
        mockMembershipStore.join(groupId, userId, joinedAt)
        sourceReviewIds.forEach { mockReviewShareStore.add(groupId, userId, it) }

        val response =
            JoinResponse(
                groupId = groupId,
                joinedAt = joinedAt.toString(),
                sharedReviewIds = sourceReviewIds,
                ticket =
                    JoinResponse.TicketConsumeSummary(
                        consumedCount = 1,
                        availableCount = mockTicketLedger.availableCount(userId),
                    ),
            )
        mockIdempotencyRegistry.register(userId, endpoint, key, request.toString(), response)

        return ResponseEntity
            .created(URI.create("/v1/groups/$groupId/memberships/me"))
            .body(response)
    }

    @Operation(summary = "탈퇴", description = "그 그룹에 공유했던 내 리뷰가 전부 내려간다 (G10). 티켓은 돌아오지 않는다 (T9).")
    @ApiErrorCodes(
        ErrorCode.GROUP_MEMBERSHIP_REQUIRED,
        ErrorCode.GROUP_NOT_FOUND,
        ErrorCode.GROUP_OWNER_CANNOT_LEAVE,
    )
    @DeleteMapping("/memberships/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun leave(
        @UserId userId: Long,
        @PathVariable groupId: String,
    ) {
        val group = findGroup(groupId)
        if (!mockMembershipStore.isMember(groupId, userId)) {
            throw TmtException(ErrorCode.GROUP_MEMBERSHIP_REQUIRED)
        }
        if (group.ownerId == userId) {
            throw TmtException(ErrorCode.GROUP_OWNER_CANNOT_LEAVE)
        }
        mockMembershipStore.leave(groupId, userId)
        mockReviewShareStore.removeUser(groupId, userId)
    }

    @Operation(summary = "리뷰 공유 목록", description = "내 리뷰 전체를 공유 여부와 함께 내린다 — PUT이 전체 교체라 현재 상태를 전부 알아야 한다.")
    @ApiErrorCodes(ErrorCode.GROUP_NOT_FOUND)
    @GetMapping("/review-shares")
    fun listReviewShares(
        @UserId userId: Long,
        @PathVariable groupId: String,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(required = false) limit: Int?,
    ): ReviewSharesResponse {
        findGroup(groupId)
        val shared = mockReviewShareStore.userShares(groupId, userId)
        val myReviews =
            mockSaveStore
                .findAll()
                .filter { it.ownerId == userId && it.reviewId != null }
                .sortedWith(compareByDescending<MockSave> { it.createdAt }.thenByDescending { it.reviewId })

        val page =
            MockCursor.paginate(myReviews, cursor, limit) { save ->
                ReviewSharesResponse.Item(
                    reviewId = save.reviewId!!,
                    placeName = placeNameOf(save.placeId),
                    thumbnailUrl = save.photoAssetIds.first().let(::mockMediaUrl),
                    contentPreview = save.content.orEmpty(),
                    isShared = save.reviewId in shared,
                    createdAt = save.createdAt.toString(),
                )
            }
        return ReviewSharesResponse(
            items = page.items,
            sharedCount = shared.size,
            nextCursor = page.nextCursor,
            hasNext = page.hasNext,
        )
    }

    @Operation(
        summary = "리뷰 공유 (전체 교체)",
        description = "보낸 reviewIds가 이 그룹에 공유된 내 리뷰의 최종 집합이 된다. 멱등이라 Idempotency-Key가 필요 없다.",
    )
    @ApiErrorCodes(
        ErrorCode.GROUP_MEMBERSHIP_REQUIRED,
        ErrorCode.GROUP_NOT_FOUND,
        ErrorCode.REVIEW_NOT_FOUND,
    )
    @PutMapping("/review-shares")
    fun replaceReviewShares(
        @UserId userId: Long,
        @PathVariable groupId: String,
        @RequestBody request: ReplaceSharesRequest,
    ): ReplaceSharesResponse {
        findGroup(groupId)
        if (!mockMembershipStore.isMember(groupId, userId)) {
            throw TmtException(ErrorCode.GROUP_MEMBERSHIP_REQUIRED)
        }
        val myReviewIds =
            mockSaveStore
                .findAll()
                .filter { it.ownerId == userId }
                .mapNotNull { it.reviewId }
                .toSet()
        request.reviewIds.firstOrNull { it !in myReviewIds }?.let {
            throw TmtException(ErrorCode.REVIEW_NOT_FOUND, it)
        }

        mockReviewShareStore.replace(groupId, userId, request.reviewIds)
        val shared = mockReviewShareStore.userShares(groupId, userId)
        return ReplaceSharesResponse(groupId = groupId, sharedReviewIds = shared.toList(), sharedCount = shared.size)
    }

    private fun findGroup(groupId: String): MockGroup =
        mockGroupStore.findById(groupId) ?: throw TmtException(ErrorCode.GROUP_NOT_FOUND)

    private fun placeNameOf(placeId: String): String = mockPlaceStore.findById(placeId)?.name ?: "(삭제된 매장)"

    data class JoinRequest(
        @field:Schema(deprecated = true, description = "단수 공유 — sourceReviewIds로 대체됐다. 둘 다 오면 합집합 (TMT-241)")
        val sourceReviewId: String? = null,
        @field:Schema(description = "가입과 함께 공유할 내 리뷰 목록 — 가입 화면의 체크박스 복수 선택 (TMT-241)")
        val sourceReviewIds: List<String>? = null,
    )

    data class JoinPreviewResponse(
        val group: GroupSummary,
        val availableTicketCount: Int,
        val requiredTicketCount: Int,
        val joinable: Boolean,
        val blockedReason: String?,
    ) {
        data class GroupSummary(
            val groupId: String,
            val name: String,
            val imageUrl: String?,
        )
    }

    data class JoinResponse(
        val groupId: String,
        val joinedAt: String,
        val sharedReviewIds: List<String>,
        val ticket: TicketConsumeSummary,
    ) {
        data class TicketConsumeSummary(
            val consumedCount: Int,
            val availableCount: Int,
        )
    }

    data class ReviewSharesResponse(
        val items: List<Item>,
        val sharedCount: Int,
        val nextCursor: String?,
        val hasNext: Boolean,
    ) {
        data class Item(
            val reviewId: String,
            val placeName: String,
            val thumbnailUrl: String,
            val contentPreview: String,
            val isShared: Boolean,
            val createdAt: String,
        )
    }

    data class ReplaceSharesRequest(
        val reviewIds: List<String>,
    )

    data class ReplaceSharesResponse(
        val groupId: String,
        val sharedReviewIds: List<String>,
        val sharedCount: Int,
    )

    companion object {
        private const val REQUIRED_TICKETS = 1
    }
}
