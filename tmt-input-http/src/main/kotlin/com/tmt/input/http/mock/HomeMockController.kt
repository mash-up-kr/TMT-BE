package com.tmt.input.http.mock

import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException
import com.tmt.input.http.auth.UserId
import com.tmt.input.http.controller.dto.response.CursorPage
import com.tmt.input.http.controller.dto.response.GroupCardResponse
import com.tmt.input.http.controller.dto.response.ReviewCardResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 홈 (명세 v2 A). 인증 필수 — 비로그인은 클라이언트가 근처보기로 라우팅한다 (A §5-2).
 * 응답이 둘로 나뉜 이유는 피드만 페이징되기 때문이다.
 */
@Tag(name = "홈 (mock)", description = "명세 v2 — A. 홈")
@RestController
@RequestMapping("/v1/home")
class HomeMockController(
    private val mockGroupStore: InMemoryStore<MockGroup>,
    private val mockSaveStore: InMemoryStore<MockSave>,
    private val mockPlaceStore: InMemoryStore<MockPlace>,
    private val mockMembershipStore: MockMembershipStore,
    private val mockReviewShareStore: MockReviewShareStore,
    private val groupAssembler: GroupAssembler,
    private val reviewCardAssembler: ReviewCardAssembler,
) {
    @Operation(summary = "인사·내 그룹·추천 그룹", description = "recommendedGroups는 myGroups가 비었을 때만 채운다 — 추천순(G17) 상위 5개.")
    @GetMapping
    fun home(
        @UserId userId: Long,
    ): HomeResponse {
        val myGroups =
            mockMembershipStore
                .joinedGroups(userId)
                .sortedBy { (_, joinedAt) -> joinedAt }
                .mapNotNull { (groupId, _) -> mockGroupStore.findById(groupId) }
                .map {
                    HomeResponse.MyGroup(
                        groupId = it.groupId,
                        name = it.name,
                        imageUrl = it.imageAssetId?.let(::mockMediaUrl),
                    )
                }

        val recommended =
            if (myGroups.isEmpty()) {
                // 탐색의 추천순(G17)과 같은 기준 — 일치 칩이 추천 이유를 그대로 설명한다
                mockGroupStore
                    .findAll()
                    .map { groupAssembler.card(it, userId) }
                    .sortedWith(GroupAssembler.RECOMMENDED_ORDER)
                    .take(RECOMMENDED_COUNT)
            } else {
                emptyList()
            }

        return HomeResponse(
            nickname = MockUsers.authorOf(userId).nickname,
            myGroups = myGroups,
            recommendedGroups = recommended,
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
        val sharedReviewIds =
            mockMembershipStore
                .joinedGroups(userId)
                .flatMap { (groupId, _) -> mockReviewShareStore.allShares(groupId) }
                .toSet()

        // 좌표는 필수다 — G19의 정렬이 거리순이고, 좌표 유무로 정렬이 갈리면 규약 §5-3에 따라
        // 이전 커서가 무효가 되는데 오프셋 커서는 그 조건을 담지 않는다. 권한 거부 시에도
        // 클라이언트가 강남역 좌표를 채우므로(E3) 좌표는 항상 온다. nearbyReviews와 같은 규칙.
        if (latitude == null || longitude == null || latitude !in -90.0..90.0 || longitude !in -180.0..180.0) {
            throw TmtException(ErrorCode.VALIDATION_FAILED, "latitude·longitude는 필수이고 위경도 범위 안이어야 합니다.")
        }

        val reviews =
            mockSaveStore
                .findAll()
                .filter { it.reviewId in sharedReviewIds }
                .sortedWith(
                    compareBy(
                        { save ->
                            mockPlaceStore
                                .findById(save.placeId)
                                ?.let { MockGeo.distanceMeters(latitude, longitude, it.latitude, it.longitude) }
                                ?: Int.MAX_VALUE
                        },
                        { it.reviewId },
                    ),
                )

        return MockCursor.paginate(
            reviews,
            cursor,
            limit,
        ) { reviewCardAssembler.assemble(it, userId, latitude, longitude) }
    }

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

    companion object {
        // 혹시, 이런 그룹은 어떠세요? 캐러셀 — 상위 5개 (A §2)
        private const val RECOMMENDED_COUNT = 5
    }
}
