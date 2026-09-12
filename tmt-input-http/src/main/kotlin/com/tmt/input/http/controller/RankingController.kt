package com.tmt.input.http.controller

import com.tmt.application.port.input.GetUserRankingsUseCase
import com.tmt.application.port.input.UserRankingKey
import com.tmt.application.port.input.UserRankingView
import com.tmt.application.port.input.UserRankingsRequest
import com.tmt.input.http.controller.dto.response.CursorPage
import com.tmt.input.http.controller.dto.response.PublicIds
import com.tmt.input.http.controller.dto.response.UserRankingResponse
import com.tmt.input.http.controller.paging.CursorCodec
import com.tmt.input.http.controller.paging.CursorCondition
import com.tmt.input.http.controller.paging.CursorSpec
import com.tmt.input.http.controller.paging.PageLimit
import io.swagger.v3.oas.annotations.Operation
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
    @Operation(summary = "유저 활동량 랭킹", description = "리뷰 수 내림차순 전체 목록. 리뷰 0건 사용자도 포함한다.")
    @GetMapping("/users")
    fun listUserRankings(
        @RequestParam(required = false) cursor: String?,
        @RequestParam(required = false) limit: Int?,
    ): CursorPage<UserRankingResponse> {
        val condition = CursorCondition.of("USER_RANKINGS")
        val after = CursorCodec.decode(UserRankingCursorSpec, cursor, condition)
        val pageLimit = PageLimit.of(limit)

        val result = getUserRankingsUseCase.get(UserRankingsRequest(after = after, limit = pageLimit))
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

    /** (reviewCount, userId) — 마지막 키는 유일해야 한다 */
    internal object UserRankingCursorSpec : CursorSpec<UserRankingKey> {
        override fun toKeys(key: UserRankingKey) = listOf(key.reviewCount.toString(), key.userId.toString())

        override fun fromKeys(keys: List<String>): UserRankingKey {
            require(keys.size == 2) { "정렬 키 2개가 필요하다" }
            return UserRankingKey(keys[0].toInt(), keys[1].toLong())
        }
    }
}
