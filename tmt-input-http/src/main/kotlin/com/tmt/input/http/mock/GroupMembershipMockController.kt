package com.tmt.input.http.mock

import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException
import com.tmt.input.http.auth.UserId
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
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
) {
    @Operation(summary = "가입 팝업 정보", description = "티켓 보유·부족 분기. preview는 참고값이고 가입이 조건을 다시 검증한다 (TX-3).")
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
    @PostMapping("/memberships")
    fun join(
        @UserId userId: Long,
        @PathVariable groupId: String,
        @RequestHeader(name = SaveMockController.IDEMPOTENCY_KEY_HEADER, required = false) idempotencyKey: String?,
        @RequestBody(required = false) request: JoinRequest?,
    ): ResponseEntity<JoinResponse> {
        if (idempotencyKey.isNullOrBlank()) {
            throw TmtException(ErrorCode.VALIDATION_FAILED, "${SaveMockController.IDEMPOTENCY_KEY_HEADER} 헤더는 필수입니다.")
        }
        findGroup(groupId)

        // 이미 가입은 티켓 부족보다 먼저 판정한다 (G8)
        if (mockMembershipStore.isMember(groupId, userId)) {
            throw TmtException(ErrorCode.ALREADY_GROUP_MEMBER)
        }

        val sourceReviewId = request?.sourceReviewId
        val sourceReview =
            sourceReviewId?.let { reviewId ->
                mockSaveStore.findAll().find { it.reviewId == reviewId && it.ownerId == userId }
                    ?: throw TmtException(ErrorCode.REVIEW_NOT_FOUND)
            }

        if (!mockTicketLedger.tryConsume(userId)) {
            throw GroupJoinTicketRequiredException(availableCount = mockTicketLedger.availableCount(userId))
        }

        val joinedAt = Instant.now()
        mockMembershipStore.join(groupId, userId, joinedAt)
        sourceReview?.let { mockReviewShareStore.add(groupId, userId, it.reviewId!!) }

        return ResponseEntity
            .created(URI.create("/v1/groups/$groupId/memberships/me"))
            .body(
                JoinResponse(
                    groupId = groupId,
                    joinedAt = joinedAt.toString(),
                    sharedReviewIds = listOfNotNull(sourceReview?.reviewId),
                    ticket =
                        JoinResponse.TicketSummary(
                            consumedCount = 1,
                            availableCount = mockTicketLedger.availableCount(userId),
                        ),
                ),
            )
    }

    @Operation(summary = "탈퇴", description = "그 그룹에 공유했던 내 리뷰가 전부 내려간다 (G10). 티켓은 돌아오지 않는다 (T9).")
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
        if (request.reviewIds.size > SHARE_LIMIT_PER_GROUP) {
            throw TmtException(ErrorCode.VALIDATION_FAILED, "reviewIds는 그룹당 최대 ${SHARE_LIMIT_PER_GROUP}건입니다.")
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
        val sourceReviewId: String? = null,
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
        val ticket: TicketSummary,
    ) {
        data class TicketSummary(
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

        // 그룹당 공유 총량 상한 (H §3-2, 팀 확인 2026-08-14)
        private const val SHARE_LIMIT_PER_GROUP = 50
    }
}
