package com.tmt.input.http.controller

import com.tmt.application.port.input.GetUserRankingsUseCase
import com.tmt.application.port.input.UserRankingKey
import com.tmt.application.port.input.UserRankingSort
import com.tmt.application.port.input.UserRankingView
import com.tmt.application.port.input.UserRankingsRequest
import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException
import com.tmt.input.http.controller.dto.response.CursorPage
import com.tmt.input.http.controller.dto.response.PublicIds
import com.tmt.input.http.controller.dto.response.UserRankingResponse
import com.tmt.input.http.controller.paging.CursorCodec
import com.tmt.input.http.controller.paging.CursorCondition
import com.tmt.input.http.controller.paging.CursorSpec
import com.tmt.input.http.controller.paging.PageLimit
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** 랭킹 (TMT-436). 비로그인도 같은 목록을 본다 — 조회자에 따라 달라지는 값이 없다. */
@Tag(name = "랭킹", description = "유저 활동량 랭킹")
@RestController
@RequestMapping("/v1/rankings")
class RankingController(
    private val getUserRankingsUseCase: GetUserRankingsUseCase,
) {
    @Operation(
        summary = "유저 활동량 랭킹",
        description = "sort 축 내림차순 전체 목록. 리뷰 0건 사용자도 포함한다. 응답 필드는 sort와 무관하게 같고 줄 순서만 바뀐다.",
    )
    @GetMapping("/users")
    fun listUserRankings(
        @Parameter(
            description = "정렬 축. 바꾸면 이전 커서는 INVALID_CURSOR다",
            schema = Schema(allowableValues = ["reviewCount", "sharedReviewCount"], defaultValue = "reviewCount"),
        )
        @RequestParam(required = false) sort: String?,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(required = false) limit: Int?,
    ): CursorPage<UserRankingResponse> {
        val rankingSort =
            UserRankingSort.fromApiValue(sort)
                ?: throw TmtException(ErrorCode.VALIDATION_FAILED, "지원하지 않는 sort 값: $sort")

        // 정렬 축이 바뀌면 이전 커서는 무효다 (규약 §5-3) — 커서 키의 의미가 달라진다
        val condition = CursorCondition.of("USER_RANKINGS", rankingSort.name)
        val after = CursorCodec.decode(UserRankingCursorSpec, cursor, condition)
        val pageLimit = PageLimit.of(limit)

        val result =
            getUserRankingsUseCase.get(
                UserRankingsRequest(sort = rankingSort, after = after, limit = pageLimit),
            )
        val nextCursor =
            if (result.hasNext) {
                result.lastKey?.let { CursorCodec.encode(UserRankingCursorSpec, it, condition) }
            } else {
                null
            }
        return CursorPage(
            items = result.items.map { it.toResponse() },
            nextCursor = nextCursor,
            hasNext = result.hasNext,
        )
    }

    private fun UserRankingView.toResponse(): UserRankingResponse =
        UserRankingResponse(
            userId = PublicIds.user(userId),
            nickname = nickname,
            profileImageUrl = profileImageUrl,
            reviewCount = reviewCount,
            sharedReviewCount = sharedReviewCount,
        )

    /** (정렬값, userId) — 마지막 키는 유일해야 한다 */
    internal object UserRankingCursorSpec : CursorSpec<UserRankingKey> {
        override fun toKeys(key: UserRankingKey) = listOf(key.sortValue.toString(), key.userId.toString())

        override fun fromKeys(keys: List<String>): UserRankingKey {
            require(keys.size == 2) { "정렬 키 2개가 필요하다" }
            return UserRankingKey(keys[0].toInt(), keys[1].toLong())
        }
    }
}
