package com.tmt.input.http.mock

import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException
import com.tmt.input.http.auth.UserId
import com.tmt.input.http.config.ApiErrorCodes
import com.tmt.input.http.controller.dto.response.ReviewCardResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "그룹 (mock)", description = "명세 v2 — D_01. 그룹 탐색 · D_02. 그룹 생성·상세·편집")
@RestController
@RequestMapping("/v1/groups")
class GroupMockController(
    private val mockGroupStore: InMemoryStore<MockGroup>,
    private val mockMembershipStore: MockMembershipStore,
    private val groupAssembler: GroupAssembler,
    private val reviewCardAssembler: ReviewCardAssembler,
) {
    @Operation(summary = "그룹 상세")
    @ApiErrorCodes(ErrorCode.GROUP_NOT_FOUND)
    @GetMapping("/{groupId}")
    fun groupDetail(
        @UserId userId: Long?,
        @PathVariable groupId: String,
    ): GroupAssembler.GroupDetailResponse = groupAssembler.detail(findGroup(groupId), userId)

    @Operation(
        summary = "그룹 상세 리뷰 목록",
        description = "미가입·비회원도 전체를 커서 페이징으로 본다. 대신 본문과 단점 요약을 서버가 마스킹한다 (G1).",
    )
    @ApiErrorCodes(ErrorCode.GROUP_NOT_FOUND)
    @GetMapping("/{groupId}/reviews")
    fun groupReviews(
        @UserId userId: Long?,
        @PathVariable groupId: String,
        @RequestParam(required = false) latitude: Double?,
        @RequestParam(required = false) longitude: Double?,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(required = false) limit: Int?,
    ): GatedReviewsResponse {
        val group = findGroup(groupId)
        val reviews = groupAssembler.sharedReviews(group.groupId)
        // 블러는 화면 표현이지만 값은 서버가 지운다 — 페이로드에 남으면 devtools로 그대로 보인다
        val masked = !mockMembershipStore.isMember(groupId, userId)

        val page =
            MockCursor.paginate(
                reviews,
                cursor,
                limit,
            ) { reviewCardAssembler.assemble(it, userId, latitude, longitude, masked) }
        return GatedReviewsResponse(
            items = page.items,
            gate =
                GatedReviewsResponse.Gate(
                    gated = masked,
                    reason = if (masked) "MEMBERSHIP_REQUIRED" else null,
                ),
            nextCursor = page.nextCursor,
            hasNext = page.hasNext,
        )
    }

    private fun findGroup(groupId: String): MockGroup =
        mockGroupStore.findById(groupId) ?: throw TmtException(ErrorCode.GROUP_NOT_FOUND)

    data class GatedReviewsResponse(
        val items: List<ReviewCardResponse>,
        val gate: Gate,
        val nextCursor: String?,
        val hasNext: Boolean,
    ) {
        data class Gate(
            val gated: Boolean,
            val reason: String?,
        )
    }

    companion object {
        private const val DESCRIPTION_MAX_LENGTH = 200
    }
}
