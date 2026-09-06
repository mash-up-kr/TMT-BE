package com.tmt.input.http.controller

import com.tmt.application.port.input.GetJoinPreviewUseCase
import com.tmt.application.port.input.IdempotentRequest
import com.tmt.application.port.input.IdempotentRequestUseCase
import com.tmt.application.port.input.JoinGroupCommand
import com.tmt.application.port.input.JoinGroupUseCase
import com.tmt.application.port.input.LeaveGroupUseCase
import com.tmt.common.exception.ErrorCode
import com.tmt.input.http.auth.UserId
import com.tmt.input.http.config.ApiErrorCodes
import com.tmt.input.http.controller.dto.response.PublicIds
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
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.net.URI

/**
 * 그룹 가입·탈퇴 실구현 (TMT-227). 응답 형태·ID 표기(`group_`·`rv_`)는 mock과 같다.
 */
@Tag(name = "그룹 가입·리뷰 공유", description = "명세 v2 — H. 그룹 게시(공유)")
@RestController
@RequestMapping("/v1/groups/{groupId}")
class GroupMembershipController(
    private val getJoinPreviewUseCase: GetJoinPreviewUseCase,
    private val joinGroupUseCase: JoinGroupUseCase,
    private val leaveGroupUseCase: LeaveGroupUseCase,
    private val idempotentRequestUseCase: IdempotentRequestUseCase,
) {
    @Operation(summary = "가입 팝업 정보", description = "티켓 보유·부족 분기. preview는 참고값이고 가입이 조건을 다시 검증한다 (TX-3).")
    @ApiErrorCodes(ErrorCode.GROUP_NOT_FOUND)
    @GetMapping("/join-preview")
    fun joinPreview(
        @UserId userId: Long,
        @PathVariable groupId: String,
    ): JoinPreviewResponse {
        val view = getJoinPreviewUseCase.get(PublicIds.parseGroupId(groupId), userId)
        return JoinPreviewResponse(
            group =
                JoinPreviewResponse.GroupSummary(
                    groupId = PublicIds.group(view.groupId),
                    name = view.name,
                    imageUrl = view.imageUrl,
                ),
            availableTicketCount = view.availableTicketCount,
            requiredTicketCount = view.requiredTicketCount,
            joinable = view.joinable,
            blockedReason = view.blockedReason?.name,
        )
    }

    @Operation(summary = "가입", description = "티켓 1장을 소비해 가입한다. 티켓 소비·멤버십 생성·자동 공유가 한 트랜잭션이다 (TX-3).")
    @ApiErrorCodes(
        ErrorCode.GROUP_NOT_FOUND,
        ErrorCode.REVIEW_NOT_FOUND,
        ErrorCode.ALREADY_GROUP_MEMBER,
        ErrorCode.GROUP_JOIN_TICKET_REQUIRED,
        ErrorCode.IDEMPOTENCY_CONFLICT,
    )
    @PostMapping("/memberships")
    @ResponseStatus(HttpStatus.CREATED)
    fun join(
        @UserId userId: Long,
        @PathVariable groupId: String,
        @IdempotencyKey key: String,
        @RequestBody(required = false) requestBody: JoinRequest?,
    ): ResponseEntity<JoinResponse> {
        val id = PublicIds.parseGroupId(groupId)
        val request = requestBody ?: JoinRequest()
        // 단수(deprecated)와 복수를 합집합으로 받는다 (TMT-241)
        val sourceReviewIds =
            (listOfNotNull(request.sourceReviewId) + request.sourceReviewIds.orEmpty())
                .map(PublicIds::parseReviewId)
                .distinct()

        val result =
            idempotentRequestUseCase.execute(
                IdempotentRequest(
                    userId = userId,
                    endpoint = "POST /v1/groups/$id/memberships",
                    idemKey = key,
                    payload = request,
                    responseType = JoinResponse::class.java,
                    successStatus = 201,
                ),
            ) {
                val joined =
                    joinGroupUseCase.join(
                        JoinGroupCommand(userId = userId, groupId = id, sourceReviewIds = sourceReviewIds),
                    )
                JoinResponse(
                    groupId = PublicIds.group(joined.groupId),
                    joinedAt = joined.joinedAt.toString(),
                    sharedReviewIds = joined.sharedReviewIds.map(PublicIds::review),
                    ticket = JoinResponse.TicketConsumeSummary(joined.consumedCount, joined.availableCount),
                )
            }
        return ResponseEntity
            .status(result.status)
            // 응답 본문의 groupId와 같은 정규화된 표기를 쓴다 — 클라이언트가 보낸 원본은 `1`일 수도 있다
            .location(URI.create("/v1/groups/${PublicIds.group(id)}/memberships/me"))
            .body(result.response)
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
    ) = leaveGroupUseCase.leave(PublicIds.parseGroupId(groupId), userId)

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
        /** `TICKET_REQUIRED` · `ALREADY_MEMBER`. joinable이면 null */
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
}
