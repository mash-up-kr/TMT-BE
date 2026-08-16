package com.tmt.input.http.mock

import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException
import com.tmt.input.http.auth.UserId
import com.tmt.input.http.controller.dto.response.CursorPage
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.time.Instant

@Tag(name = "그룹 (mock)", description = "명세 v2 — D_01. 그룹 탐색 · D_02. 그룹 생성·상세·편집")
@RestController
@RequestMapping("/v1/groups")
class GroupMockController(
    private val mockGroupStore: InMemoryStore<MockGroup>,
    private val mockAssetStore: InMemoryStore<MockAsset>,
    private val mockMembershipStore: MockMembershipStore,
    private val groupAssembler: GroupAssembler,
    private val reviewCardAssembler: ReviewCardAssembler,
) {
    @Operation(summary = "그룹 탐색", description = "검색·필터·정렬이 모두 한 목록에 걸린다. 파라미터 없이 부르면 추천순 전체 목록.")
    @GetMapping
    fun listGroups(
        @UserId userId: Long?,
        @RequestParam(required = false) query: String?,
        @RequestParam(required = false) foodCategoryId: String?,
        @RequestParam(required = false) regionTagIds: List<String>?,
        @RequestParam(required = false) sort: String?,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(required = false) limit: Int?,
    ): CursorPage<GroupAssembler.GroupCardResponse> {
        foodCategoryId?.let {
            if (it !in
                GroupTags.FOOD_CATEGORY_IDS
            ) {
                throw TmtException(ErrorCode.GROUP_TAG_NOT_FOUND, it)
            }
        }
        regionTagIds?.forEach {
            if (it !in
                GroupTags.REGION_TAG_IDS
            ) {
                throw TmtException(ErrorCode.GROUP_TAG_NOT_FOUND, it)
            }
        }

        var groups = mockGroupStore.findAll()
        query?.takeIf { it.isNotBlank() }?.let { q ->
            // 검색 대상은 그룹명·한줄 소개·그룹 태그다. 공유된 매장명은 대상이 아니다 (G18)
            groups =
                groups.filter { g ->
                    g.name.contains(q, ignoreCase = true) ||
                        g.oneLineDescription.contains(q, ignoreCase = true) ||
                        GroupTags.foodLabelOf(g.foodCategoryId).contains(q) ||
                        g.regionTagIds.any { GroupTags.regionLabelOf(it).contains(q) }
                }
        }
        foodCategoryId?.let { groups = groups.filter { g -> g.foodCategoryId == it } }
        regionTagIds?.takeIf { it.isNotEmpty() }?.let { regions ->
            groups = groups.filter { g -> g.regionTagIds.any { it in regions } }
        }

        val cards = groups.map { it to groupAssembler.card(it, userId) }
        val sorted =
            when (sort ?: SORT_RECOMMENDED) {
                // 추천순: 내 저장 매장과 겹치는 그룹 → 가입자 수 → groupId (G17)
                SORT_RECOMMENDED ->
                    cards.sortedWith(
                        compareByDescending<Pair<MockGroup, GroupAssembler.GroupCardResponse>> {
                            it.second.matchedSavedPlaceCount
                        }.thenByDescending { it.second.memberCount }
                            .thenByDescending { groupSeq(it.first.groupId) },
                    )

                SORT_MEMBER_COUNT ->
                    cards.sortedWith(
                        compareByDescending<Pair<MockGroup, GroupAssembler.GroupCardResponse>> { it.second.memberCount }
                            .thenByDescending { groupSeq(it.first.groupId) },
                    )

                SORT_REVIEW_COUNT ->
                    cards.sortedWith(
                        compareByDescending<Pair<MockGroup, GroupAssembler.GroupCardResponse>> { it.second.reviewCount }
                            .thenByDescending { groupSeq(it.first.groupId) },
                    )

                else -> throw TmtException(ErrorCode.VALIDATION_FAILED, "지원하지 않는 sort 값: $sort")
            }

        return MockCursor.paginate(sorted, cursor, limit) { it.second }
    }

    @Operation(summary = "그룹 이름 중복 확인", description = "참고값이다 — 생성이 유일성을 다시 검증하고 GROUP_NAME_DUPLICATED로 거절한다.")
    @GetMapping("/name-availability")
    fun nameAvailability(
        @UserId userId: Long,
        @RequestParam(required = false) name: String?,
    ): NameAvailabilityResponse {
        if (name.isNullOrBlank()) {
            throw TmtException(ErrorCode.VALIDATION_FAILED, "name은 필수입니다.")
        }
        return NameAvailabilityResponse(name = name, available = mockGroupStore.findAll().none { it.name == name })
    }

    @Operation(summary = "그룹 만들기", description = "4단계 입력을 한 번에 보낸다. 요청자가 자동으로 그룹장이 되고 소유권은 이전되지 않는다 (G13).")
    @PostMapping
    fun createGroup(
        @UserId userId: Long,
        @RequestBody request: GroupRequest,
    ): ResponseEntity<GroupAssembler.GroupDetailResponse> {
        validate(request, requesterId = userId)
        if (mockGroupStore.findAll().any { it.name == request.name }) {
            throw TmtException(ErrorCode.GROUP_NAME_DUPLICATED)
        }

        val group =
            mockGroupStore.create { id ->
                MockGroup(
                    groupId = id,
                    name = request.name,
                    oneLineDescription = request.oneLineDescription,
                    description = request.description,
                    imageAssetId = request.imageAssetId,
                    foodCategoryId = request.foodCategoryId,
                    regionTagIds = request.regionTagIds,
                    ownerId = userId,
                    createdAt = Instant.now(),
                )
            }
        // 그룹장은 탈퇴할 수 없다(G11) = 생성자는 멤버다. 자기 그룹 가입에 티켓은 들지 않는다
        mockMembershipStore.join(group.groupId, userId, group.createdAt)
        attachImage(newAssetId = group.imageAssetId, previousAssetId = null)

        return ResponseEntity
            .created(URI.create("/v1/groups/${group.groupId}"))
            .body(groupAssembler.detail(group, userId))
    }

    @Operation(summary = "그룹 상세")
    @GetMapping("/{groupId}")
    fun groupDetail(
        @UserId userId: Long?,
        @PathVariable groupId: String,
    ): GroupAssembler.GroupDetailResponse = groupAssembler.detail(findGroup(groupId), userId)

    @Operation(summary = "그룹 상세 리뷰 목록", description = "게이트가 걸리는 곳 — 미가입·비회원은 최신 3건, 가입자는 전체 (G1). 판정은 가입 여부 하나다.")
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

        if (!mockMembershipStore.isMember(groupId, userId)) {
            // 미가입일 때 hasNext는 항상 false — 더 있다고 알리면 화면이 잘못된 무한 스크롤을 만든다
            return GatedReviewsResponse(
                items =
                    reviews
                        .take(
                            GATE_VISIBLE_COUNT,
                        ).map { reviewCardAssembler.assemble(it, userId, latitude, longitude) },
                gate =
                    GatedReviewsResponse.Gate(
                        gated = true,
                        reason = "MEMBERSHIP_REQUIRED",
                        visibleCount = GATE_VISIBLE_COUNT,
                    ),
                nextCursor = null,
                hasNext = false,
            )
        }

        val page =
            MockCursor.paginate(
                reviews,
                cursor,
                limit,
            ) { reviewCardAssembler.assemble(it, userId, latitude, longitude) }
        return GatedReviewsResponse(
            items = page.items,
            gate = GatedReviewsResponse.Gate(gated = false, reason = null, visibleCount = null),
            nextCursor = page.nextCursor,
            hasNext = page.hasNext,
        )
    }

    @Operation(summary = "그룹 편집", description = "생성자만 호출할 수 있다 (G13). 전체 교체라 바꾸지 않는 필드도 현재 값을 실어 보내야 한다.")
    @PutMapping("/{groupId}")
    fun updateGroup(
        @UserId userId: Long,
        @PathVariable groupId: String,
        @RequestBody request: GroupRequest,
    ): GroupAssembler.GroupDetailResponse {
        val group = findGroup(groupId)
        if (group.ownerId != userId) {
            throw TmtException(ErrorCode.GROUP_OWNER_REQUIRED)
        }
        validate(request, requesterId = userId, currentImageAssetId = group.imageAssetId)
        if (mockGroupStore.findAll().any { it.name == request.name && it.groupId != groupId }) {
            throw TmtException(ErrorCode.GROUP_NAME_DUPLICATED)
        }

        val updated =
            mockGroupStore.update(groupId) {
                it.copy(
                    name = request.name,
                    oneLineDescription = request.oneLineDescription,
                    description = request.description,
                    imageAssetId = request.imageAssetId,
                    foodCategoryId = request.foodCategoryId,
                    regionTagIds = request.regionTagIds,
                )
            } ?: throw TmtException(ErrorCode.GROUP_NOT_FOUND)
        attachImage(newAssetId = updated.imageAssetId, previousAssetId = group.imageAssetId)
        return groupAssembler.detail(updated, userId)
    }

    private fun findGroup(groupId: String): MockGroup =
        mockGroupStore.findById(groupId) ?: throw TmtException(ErrorCode.GROUP_NOT_FOUND)

    private fun validate(
        request: GroupRequest,
        requesterId: Long,
        currentImageAssetId: String? = null,
    ) {
        if (request.name.isBlank() || request.oneLineDescription.isBlank()) {
            throw TmtException(ErrorCode.VALIDATION_FAILED, "name과 oneLineDescription은 필수입니다.")
        }
        if (request.regionTagIds.isEmpty()) {
            throw TmtException(ErrorCode.VALIDATION_FAILED, "regionTagIds는 최소 1개입니다.")
        }
        if ((request.description?.length ?: 0) > DESCRIPTION_MAX_LENGTH) {
            throw TmtException(ErrorCode.VALIDATION_FAILED, "description은 최대 ${DESCRIPTION_MAX_LENGTH}자입니다.")
        }
        if (request.foodCategoryId !in GroupTags.FOOD_CATEGORY_IDS) {
            throw TmtException(ErrorCode.GROUP_TAG_NOT_FOUND, request.foodCategoryId)
        }
        request.regionTagIds.forEach {
            if (it !in
                GroupTags.REGION_TAG_IDS
            ) {
                throw TmtException(ErrorCode.GROUP_TAG_NOT_FOUND, it)
            }
        }
        request.imageAssetId?.let { assetId ->
            val asset = mockAssetStore.findById(assetId)
            if (asset == null || asset.ownerId != requesterId) {
                throw TmtException(ErrorCode.MEDIA_NOT_OWNED)
            }
            if (asset.attached && assetId != currentImageAssetId) {
                throw TmtException(ErrorCode.MEDIA_ALREADY_ATTACHED)
            }
        }
    }

    /**
     * 그룹 대표 이미지도 리뷰 사진과 같은 업로드 경로를 쓰므로(M7) 붙일 때 ATTACHED로 전이시켜야
     * 한다 — STAGED로 남으면 TTL 정리(M4)가 사용 중인 그룹 이미지를 지운다.
     * 이미지를 교체하면 이전 asset은 STAGED로 되돌려 TTL 정리 대상이 되게 한다.
     */
    private fun attachImage(
        newAssetId: String?,
        previousAssetId: String?,
    ) {
        if (newAssetId == previousAssetId) return
        previousAssetId?.let { mockAssetStore.update(it) { asset -> asset.copy(attached = false) } }
        newAssetId?.let { mockAssetStore.update(it) { asset -> asset.copy(attached = true) } }
    }

    data class GroupRequest(
        val name: String,
        val oneLineDescription: String,
        val foodCategoryId: String,
        val regionTagIds: List<String>,
        val imageAssetId: String? = null,
        val description: String? = null,
    )

    data class NameAvailabilityResponse(
        val name: String,
        val available: Boolean,
    )

    data class GatedReviewsResponse(
        val items: List<ReviewCardAssembler.ReviewCardResponse>,
        val gate: Gate,
        val nextCursor: String?,
        val hasNext: Boolean,
    ) {
        data class Gate(
            val gated: Boolean,
            val reason: String?,
            val visibleCount: Int?,
        )
    }

    companion object {
        const val SORT_RECOMMENDED = "RECOMMENDED"
        const val SORT_MEMBER_COUNT = "MEMBER_COUNT"
        const val SORT_REVIEW_COUNT = "REVIEW_COUNT"

        // 미가입 게이트 — 최신 3건까지 (G1, 확정표 "그룹 리뷰 열람 게이트")
        private const val GATE_VISIBLE_COUNT = 3
        private const val DESCRIPTION_MAX_LENGTH = 200

        private fun groupSeq(groupId: String): Long = groupId.substringAfterLast('_').toLongOrNull() ?: 0
    }
}
