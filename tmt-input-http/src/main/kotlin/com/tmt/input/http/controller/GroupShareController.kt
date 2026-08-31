package com.tmt.input.http.controller

import com.tmt.application.port.input.GetReviewSharesUseCase
import com.tmt.application.port.input.ReplaceReviewSharesUseCase
import com.tmt.application.port.input.ReviewShareKey
import com.tmt.application.port.input.ReviewSharesRequest
import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException
import com.tmt.input.http.auth.UserId
import com.tmt.input.http.config.ApiErrorCodes
import com.tmt.input.http.controller.dto.response.PublicIds
import com.tmt.input.http.controller.paging.CursorCodec
import com.tmt.input.http.controller.paging.CursorCondition
import com.tmt.input.http.controller.paging.CursorSpec
import com.tmt.input.http.controller.paging.PageLimit
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

/**
 * 리뷰 공유 집합 실구현 (TMT-223). 응답 형태·ID 표기(`rv_`)는 mock과 같다.
 */
@Tag(name = "그룹 가입·리뷰 공유 (mock)", description = "명세 v2 — H. 그룹 게시(공유)")
@RestController
@RequestMapping("/v1/groups/{groupId}/review-shares")
class GroupShareController(
    private val getReviewSharesUseCase: GetReviewSharesUseCase,
    private val replaceReviewSharesUseCase: ReplaceReviewSharesUseCase,
) {
    @Operation(summary = "리뷰 공유 목록", description = "내 리뷰 전체를 공유 여부와 함께 내린다 — PUT이 전체 교체라 현재 상태를 전부 알아야 한다.")
    @ApiErrorCodes(ErrorCode.GROUP_NOT_FOUND)
    @GetMapping
    fun listReviewShares(
        @UserId userId: Long,
        @PathVariable groupId: String,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(required = false) limit: Int?,
    ): ReviewSharesResponse {
        val id = parseGroupId(groupId)
        val condition = CursorCondition.of("REVIEW_SHARES", id, userId)
        val after = CursorCodec.decode(ShareCursorSpec, cursor, condition)

        val result =
            getReviewSharesUseCase.get(
                ReviewSharesRequest(userId = userId, groupId = id, after = after, limit = PageLimit.of(limit)),
            )
        val nextCursor =
            if (result.hasNext) {
                result.lastKey?.let { CursorCodec.encode(ShareCursorSpec, it, condition) }
            } else {
                null
            }
        return ReviewSharesResponse(
            items =
                result.items.map {
                    ReviewSharesResponse.Item(
                        reviewId = PublicIds.review(it.reviewId),
                        placeName = it.placeName,
                        thumbnailUrl = it.thumbnailUrl,
                        contentPreview = it.contentPreview,
                        isShared = it.isShared,
                        createdAt = it.createdAt.toString(),
                    )
                },
            sharedCount = result.sharedCount,
            nextCursor = nextCursor,
            hasNext = result.hasNext,
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
    @PutMapping
    fun replaceReviewShares(
        @UserId userId: Long,
        @PathVariable groupId: String,
        @RequestBody request: ReplaceSharesRequest,
    ): ReplaceSharesResponse {
        val id = parseGroupId(groupId)
        val result = replaceReviewSharesUseCase.replace(id, userId, request.reviewIds.map(::parseReviewId))
        return ReplaceSharesResponse(
            groupId = PublicIds.group(id),
            sharedReviewIds = result.sharedReviewIds.map(PublicIds::review),
            sharedCount = result.sharedCount,
        )
    }

    /** 접두·형식이 어긋나면 없는 자원과 같다 — 존재 여부를 새지 않게 NOT_FOUND 계열로 던진다. */
    private fun parseGroupId(publicId: String): Long =
        publicId.removePrefix("group_").toLongOrNull() ?: throw TmtException(ErrorCode.GROUP_NOT_FOUND)

    private fun parseReviewId(publicId: String): Long =
        publicId.removePrefix("rv_").toLongOrNull() ?: throw TmtException(ErrorCode.REVIEW_NOT_FOUND, publicId)

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

    /** (createdAt, reviewId) — 마지막 키는 유일해야 한다 (TMT-178) */
    internal object ShareCursorSpec : CursorSpec<ReviewShareKey> {
        override fun toKeys(key: ReviewShareKey) = listOf(key.createdAt.toString(), key.reviewId.toString())

        override fun fromKeys(keys: List<String>): ReviewShareKey {
            require(keys.size == 2) { "정렬 키 2개가 필요하다" }
            return ReviewShareKey(Instant.parse(keys[0]), keys[1].toLong())
        }
    }
}
