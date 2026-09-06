package com.tmt.input.http.controller

import com.tmt.application.port.input.GetHomeFeedUseCase
import com.tmt.application.port.input.GetHomeUseCase
import com.tmt.application.port.input.GroupCardView
import com.tmt.application.port.input.HomeFeedKey
import com.tmt.application.port.input.HomeFeedRequest
import com.tmt.input.http.auth.UserId
import com.tmt.input.http.controller.dto.response.CursorPage
import com.tmt.input.http.controller.dto.response.GroupCardResponse
import com.tmt.input.http.controller.dto.response.PublicIds
import com.tmt.input.http.controller.dto.response.ReviewCardResponse
import com.tmt.input.http.controller.dto.response.toResponse
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
import java.time.Instant

/**
 * 홈 실구현 (TMT-230). 응답 형태·ID 표기(`rv_`·`place_`·`group_`)는 mock과 같다.
 * 인증 필수 — 비로그인은 클라이언트가 근처보기로 라우팅한다 (A §5-2).
 * 응답이 둘로 나뉜 이유는 피드만 페이징되기 때문이다.
 */
@Tag(name = "홈", description = "명세 v2 — A. 홈")
@RestController
@RequestMapping("/v1/home")
class HomeController(
    private val getHomeUseCase: GetHomeUseCase,
    private val getHomeFeedUseCase: GetHomeFeedUseCase,
) {
    @Operation(
        summary = "인사·내 그룹·추천 그룹",
        description = "recommendedGroups는 가입 여부와 무관하게 채운다 — 이미 가입한 그룹을 뺀 추천순(G17) 상위 5개.",
    )
    @GetMapping
    fun home(
        @UserId userId: Long,
    ): HomeResponse {
        val result = getHomeUseCase.get(userId)
        return HomeResponse(
            nickname = result.nickname,
            myGroups =
                result.myGroups.map {
                    HomeResponse.MyGroup(
                        groupId = PublicIds.group(it.groupId),
                        name = it.name,
                        imageUrl = it.imageUrl,
                    )
                },
            recommendedGroups = result.recommendedGroups.map(::toGroupCard),
        )
    }

    @Operation(
        summary = "리뷰 피드",
        description = "가입한 그룹들에 공유된 리뷰를 하나로 합쳐 내린다. 같은 리뷰가 여러 그룹에 공유돼 있어도 한 번만 내린다 (G19).",
    )
    @GetMapping("/feed")
    fun feed(
        @UserId userId: Long,
        @RequestParam(required = false) latitude: Double?,
        @RequestParam(required = false) longitude: Double?,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(required = false) limit: Int?,
    ): CursorPage<ReviewCardResponse> {
        // 좌표 유무로 정렬 자체가 갈리고 좌표가 바뀌면 거리 정렬이 바뀐다 — 어느 쪽이든 이전 커서는 무효다 (규약 §5-3)
        val condition = CursorCondition.of("HOME_FEED", latitude, longitude)
        val spec = if (latitude != null && longitude != null) DistanceCursorSpec else RecencyCursorSpec
        val after = CursorCodec.decode(spec, cursor, condition)

        val result =
            getHomeFeedUseCase.get(
                HomeFeedRequest(
                    viewerId = userId,
                    latitude = latitude,
                    longitude = longitude,
                    after = after,
                    limit = PageLimit.of(limit),
                ),
            )
        val nextCursor =
            if (result.hasNext) result.lastKey?.let { CursorCodec.encode(spec, it, condition) } else null
        return CursorPage(
            items = result.items.map { it.toResponse() },
            nextCursor = nextCursor,
            hasNext = result.hasNext,
        )
    }

    private fun toGroupCard(view: GroupCardView) =
        GroupCardResponse(
            groupId = PublicIds.group(view.groupId),
            name = view.name,
            oneLineDescription = view.oneLineDescription,
            coverImageUrl = view.coverImageUrl,
            memberCount = view.memberCount,
            reviewCount = view.reviewCount,
            placeCount = view.placeCount,
            matchedSavedPlaceCount = view.matchedSavedPlaceCount,
        )

    data class HomeResponse(
        val nickname: String,
        val myGroups: List<MyGroup>,
        val recommendedGroups: List<GroupCardResponse>,
    ) {
        data class MyGroup(
            val groupId: String,
            val name: String,
            val imageUrl: String?,
        )
    }

    /** (distanceMeters, reviewId) — 마지막 키는 유일해야 한다 (TMT-178) */
    internal object DistanceCursorSpec : CursorSpec<HomeFeedKey> {
        override fun toKeys(key: HomeFeedKey) =
            listOf(requireNotNull(key.distanceMeters).toString(), key.reviewId.toString())

        override fun fromKeys(keys: List<String>): HomeFeedKey {
            require(keys.size == 2) { "정렬 키 2개가 필요하다" }
            return HomeFeedKey(distanceMeters = keys[0].toInt(), createdAt = null, reviewId = keys[1].toLong())
        }
    }

    /** (createdAt, reviewId) — 좌표 없는 요청의 대체 정렬. createdAt은 ISO-8601로 실어 정밀도를 잃지 않는다 */
    internal object RecencyCursorSpec : CursorSpec<HomeFeedKey> {
        override fun toKeys(key: HomeFeedKey) =
            listOf(requireNotNull(key.createdAt).toString(), key.reviewId.toString())

        override fun fromKeys(keys: List<String>): HomeFeedKey {
            require(keys.size == 2) { "정렬 키 2개가 필요하다" }
            return HomeFeedKey(distanceMeters = null, createdAt = Instant.parse(keys[0]), reviewId = keys[1].toLong())
        }
    }
}
