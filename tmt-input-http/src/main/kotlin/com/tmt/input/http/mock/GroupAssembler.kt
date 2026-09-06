package com.tmt.input.http.mock

import com.tmt.input.http.controller.dto.response.GroupCardResponse

/** GroupCard(D_01 §2)·그룹 상세(D_02 §3-1) 조립 — 집계와 커버는 공유 리뷰에서 파생한다. */
class GroupAssembler(
    private val mockMediaUrls: MockMediaUrls,
    private val saveStore: InMemoryStore<MockSave>,
    private val membershipStore: MockMembershipStore,
    private val shareStore: MockReviewShareStore,
) {
    fun card(
        group: MockGroup,
        viewerId: Long?,
    ): GroupCardResponse {
        val stats = statsOf(group.groupId)
        return GroupCardResponse(
            groupId = group.groupId,
            name = group.name,
            oneLineDescription = group.oneLineDescription,
            coverImageUrl = stats.coverPhotoUrls.firstOrNull(),
            memberCount = membershipStore.memberCount(group.groupId),
            reviewCount = stats.reviewCount,
            placeCount = stats.placeCount,
            matchedSavedPlaceCount = matchedSavedPlaceCount(group.groupId, viewerId),
        )
    }

    /** 그룹에 공유된 완성 리뷰들 (최신순). 게이트 목록·집계·커버가 전부 여기서 나온다. */
    fun sharedReviews(groupId: String): List<MockSave> {
        val reviewIds = shareStore.allShares(groupId)
        return saveStore
            .findAll()
            .filter { it.reviewId in reviewIds }
            .sortedWith(compareByDescending<MockSave> { it.createdAt }.thenByDescending { it.reviewId })
    }

    private fun statsOf(groupId: String): GroupStats {
        val reviews = sharedReviews(groupId)
        return GroupStats(
            reviewCount = reviews.size,
            placeCount = reviews.map { it.placeId }.distinct().size,
            coverPhotoUrls =
                reviews.flatMap { save -> save.photoAssetIds.map { mockMediaUrls.urlOf(it) } },
        )
    }

    /** 내가 저장한 가게와 N개 일치해요 — 저장(Save) 기준이지 찜이 아니다 (G12). 비로그인이면 0. */
    private fun matchedSavedPlaceCount(
        groupId: String,
        viewerId: Long?,
    ): Int {
        if (viewerId == null) return 0
        val myPlaceIds =
            saveStore
                .findAll()
                .filter { it.ownerId == viewerId }
                .map { it.placeId }
                .toSet()
        val groupPlaceIds = sharedReviews(groupId).map { it.placeId }.toSet()
        return (myPlaceIds intersect groupPlaceIds).size
    }

    private data class GroupStats(
        val reviewCount: Int,
        val placeCount: Int,
        val coverPhotoUrls: List<String>,
    )

    companion object {
        /**
         * 추천순 정렬 (G17) — 내 저장 매장과 겹치는 수 → 가입자 수 → groupId.
         * 그룹 탐색과 홈 추천 캐러셀이 같은 기준을 써야 두 화면이 갈리지 않는다.
         * 카드만으로 세 키가 모두 나오므로 원본 그룹을 함께 들고 다닐 필요가 없다.
         */
        val RECOMMENDED_ORDER: Comparator<GroupCardResponse> =
            compareByDescending<GroupCardResponse> { it.matchedSavedPlaceCount }
                .thenByDescending { it.memberCount }
                .thenByDescending { it.groupId.substringAfterLast('_').toLongOrNull() ?: 0 }
    }
}
