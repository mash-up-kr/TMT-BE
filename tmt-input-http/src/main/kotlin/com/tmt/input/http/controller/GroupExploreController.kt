package com.tmt.input.http.controller

import com.tmt.application.port.input.CheckGroupNameUseCase
import com.tmt.application.port.input.GetGroupsUseCase
import com.tmt.application.port.input.GroupCardView
import com.tmt.application.port.input.GroupListKey
import com.tmt.application.port.input.GroupSort
import com.tmt.application.port.input.GroupsRequest
import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException
import com.tmt.input.http.auth.UserId
import com.tmt.input.http.config.ApiErrorCodes
import com.tmt.input.http.controller.dto.response.CursorPage
import com.tmt.input.http.controller.dto.response.GroupCardResponse
import com.tmt.input.http.controller.dto.response.PublicIds
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

/**
 * 그룹 탐색 실구현 (TMT-220). 응답 형태·ID 표기(`group_`)는 mock과 같다.
 */
@Tag(name = "그룹", description = "명세 v2 — D_01. 그룹 탐색 · D_02. 그룹 생성·상세·편집")
@RestController
@RequestMapping("/v1/groups")
class GroupExploreController(
    private val getGroupsUseCase: GetGroupsUseCase,
    private val checkGroupNameUseCase: CheckGroupNameUseCase,
) {
    @Operation(summary = "그룹 탐색", description = "검색·필터·정렬이 모두 한 목록에 걸린다. 파라미터 없이 부르면 추천순 전체 목록.")
    @ApiErrorCodes(ErrorCode.GROUP_TAG_NOT_FOUND)
    @GetMapping
    fun listGroups(
        @UserId userId: Long?,
        @RequestParam(required = false) query: String?,
        @RequestParam(required = false) foodCategoryId: String?,
        @RequestParam(required = false) regionTagIds: List<String>?,
        @RequestParam(required = false) sort: String?,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(required = false) limit: Int?,
    ): CursorPage<GroupCardResponse> {
        val groupSort =
            runCatching { GroupSort.valueOf(sort ?: GroupSort.RECOMMENDED.name) }
                .getOrElse { throw TmtException(ErrorCode.VALIDATION_FAILED, "지원하지 않는 sort 값: $sort") }

        // 검색·필터·정렬이 바뀌면 이전 커서는 무효다 (규약 §5-3).
        // RECOMMENDED는 1차 키(일치 저장 수)가 조회자마다 달라 조회자도 조건이다.
        val condition =
            CursorCondition.of(
                "GROUPS",
                query,
                foodCategoryId,
                regionTagIds?.sorted()?.joinToString(","),
                groupSort.name,
                if (groupSort == GroupSort.RECOMMENDED) userId else null,
            )
        val after = CursorCodec.decode(GroupCursorSpec, cursor, condition)
        val pageLimit = PageLimit.of(limit)

        val result =
            getGroupsUseCase.get(
                GroupsRequest(
                    viewerId = userId,
                    query = query,
                    foodCategoryId = foodCategoryId,
                    regionTagIds = regionTagIds.orEmpty(),
                    sort = groupSort,
                    after = after,
                    limit = pageLimit,
                ),
            )
        val nextCursor =
            if (result.hasNext) {
                result.lastKey?.let { CursorCodec.encode(GroupCursorSpec, it, condition) }
            } else {
                null
            }
        return CursorPage(
            items = result.items.map { it.toResponse() },
            nextCursor = nextCursor,
            hasNext = result.hasNext,
        )
    }

    @Operation(summary = "그룹 이름 중복 확인", description = "참고값이다 — 생성이 유일성을 다시 검증하고 GROUP_NAME_DUPLICATED로 거절한다.")
    @GetMapping("/name-availability")
    fun nameAvailability(
        @UserId userId: Long,
        @RequestParam name: String,
    ): NameAvailabilityResponse {
        // 누락은 Spring이 400으로 거른다(ExceptionAdvice). 빈 문자열(`?name=`)은 누락이 아니므로 여기서 본다
        if (name.isBlank()) {
            throw TmtException(ErrorCode.VALIDATION_FAILED, "name은 비어 있을 수 없습니다.")
        }
        return NameAvailabilityResponse(name = name, available = checkGroupNameUseCase.isAvailable(name))
    }

    private fun GroupCardView.toResponse(): GroupCardResponse =
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

    data class NameAvailabilityResponse(
        val name: String,
        val available: Boolean,
    )

    /** (k1, k2, groupId) — 마지막 키는 유일해야 한다 (TMT-178) */
    internal object GroupCursorSpec : CursorSpec<GroupListKey> {
        override fun toKeys(key: GroupListKey) = listOf(key.k1.toString(), key.k2.toString(), key.groupId.toString())

        override fun fromKeys(keys: List<String>): GroupListKey {
            require(keys.size == 3) { "정렬 키 3개가 필요하다" }
            return GroupListKey(keys[0].toLong(), keys[1].toLong(), keys[2].toLong())
        }
    }
}
