package com.tmt.input.http.controller

import com.tmt.application.port.input.GetGroupDetailUseCase
import com.tmt.application.port.input.GetGroupReviewsUseCase
import com.tmt.application.port.input.GroupReviewKey
import com.tmt.application.port.input.GroupReviewsRequest
import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException
import com.tmt.input.http.auth.UserId
import com.tmt.input.http.config.ApiErrorCodes
import com.tmt.input.http.controller.dto.response.GroupDetailResponse
import com.tmt.input.http.controller.dto.response.ReviewCardResponse
import com.tmt.input.http.controller.dto.response.toResponse
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
 * 그룹 상세·리뷰 목록 실구현 (TMT-222). 응답 형태·ID 표기(`group_`)는 mock과 같다.
 */
@Tag(name = "그룹 (mock)", description = "명세 v2 — D_01. 그룹 탐색 · D_02. 그룹 생성·상세·편집")
@RestController
@RequestMapping("/v1/groups/{groupId}")
class GroupQueryController(
    private val getGroupDetailUseCase: GetGroupDetailUseCase,
    private val getGroupReviewsUseCase: GetGroupReviewsUseCase,
) {
    @Operation(summary = "그룹 상세")
    @ApiErrorCodes(ErrorCode.GROUP_NOT_FOUND)
    @GetMapping
    fun groupDetail(
        @UserId userId: Long?,
        @PathVariable groupId: String,
    ): GroupDetailResponse = getGroupDetailUseCase.get(parseGroupId(groupId), userId).toResponse()

    @Operation(
        summary = "그룹 상세 리뷰 목록",
        description = "미가입·비회원도 전체를 커서 페이징으로 본다. 대신 본문과 단점 요약을 서버가 마스킹한다 (G1).",
    )
    @ApiErrorCodes(ErrorCode.GROUP_NOT_FOUND)
    @GetMapping("/reviews")
    fun groupReviews(
        @UserId userId: Long?,
        @PathVariable groupId: String,
        @RequestParam(required = false) latitude: Double?,
        @RequestParam(required = false) longitude: Double?,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(required = false) limit: Int?,
    ): GatedReviewsResponse {
        val id = parseGroupId(groupId)
        // 좌표가 바뀌면 거리 값이 바뀔 뿐 정렬은 최신순 그대로라 조건에 넣지 않는다 (B §3-2와 동일)
        val condition = CursorCondition.of("GROUP_REVIEWS", id)
        val after = CursorCodec.decode(GroupReviewCursorSpec, cursor, condition)

        val result =
            getGroupReviewsUseCase.get(
                GroupReviewsRequest(
                    viewerId = userId,
                    groupId = id,
                    viewerLatitude = latitude,
                    viewerLongitude = longitude,
                    after = after,
                    limit = PageLimit.of(limit),
                ),
            )
        val nextCursor =
            if (result.hasNext) {
                result.lastKey?.let { CursorCodec.encode(GroupReviewCursorSpec, it, condition) }
            } else {
                null
            }
        return GatedReviewsResponse(
            items = result.items.map { it.toResponse(masked = result.gated) },
            gate =
                GatedReviewsResponse.Gate(
                    gated = result.gated,
                    reason = if (result.gated) "MEMBERSHIP_REQUIRED" else null,
                ),
            nextCursor = nextCursor,
            hasNext = result.hasNext,
        )
    }

    /** 접두·형식이 어긋나면 없는 자원과 같다 — 존재 여부를 새지 않게 NOT_FOUND 계열로 던진다. */
    private fun parseGroupId(publicId: String): Long =
        publicId.removePrefix("group_").toLongOrNull() ?: throw TmtException(ErrorCode.GROUP_NOT_FOUND)

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

    /** (createdAt, reviewId) — 마지막 키는 유일해야 한다 (TMT-178) */
    internal object GroupReviewCursorSpec : CursorSpec<GroupReviewKey> {
        override fun toKeys(key: GroupReviewKey) = listOf(key.createdAt.toString(), key.reviewId.toString())

        override fun fromKeys(keys: List<String>): GroupReviewKey {
            require(keys.size == 2) { "정렬 키 2개가 필요하다" }
            return GroupReviewKey(Instant.parse(keys[0]), keys[1].toLong())
        }
    }
}
