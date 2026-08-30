package com.tmt.application.port.input

import java.time.Instant

/** 홈 상단 (A §2) — 인사·내 그룹·추천 그룹. 인증 필수라 viewerId는 non-null이다. */
interface GetHomeUseCase {
    fun get(viewerId: Long): HomeResult
}

data class HomeResult(
    val nickname: String,
    val myGroups: List<MyGroup>,
    val recommendedGroups: List<GroupCardView>,
) {
    data class MyGroup(
        val groupId: Long,
        val name: String,
        val imageUrl: String?,
    )
}

/** GroupCard(D_01 §2)의 읽기 모델 — 그룹 목록·홈 추천 캐러셀이 같은 카드를 쓴다 (TMT-180). */
data class GroupCardView(
    val groupId: Long,
    val name: String,
    val oneLineDescription: String,
    val coverImageUrl: String?,
    val memberCount: Int,
    val reviewCount: Int,
    val placeCount: Int,
    val matchedSavedPlaceCount: Int,
)

/**
 * 홈 피드 (A §3) — 가입한 그룹들에 공유된 리뷰를 하나로 합쳐 내린다 (G19).
 * 좌표가 오면 거리순, 없으면 최신순이다.
 */
interface GetHomeFeedUseCase {
    fun get(request: HomeFeedRequest): HomeFeedResult
}

data class HomeFeedRequest(
    val viewerId: Long,
    val latitude: Double?,
    val longitude: Double?,
    /** 이전 페이지 마지막 행의 정렬 키. 커서 문자열의 해석·발급은 어댑터(컨트롤러) 몫이다. */
    val after: HomeFeedKey? = null,
    val limit: Int,
)

/**
 * 정렬 키. 거리순이면 [distanceMeters], 최신순이면 [createdAt]이 채워진다.
 * 어느 쪽이든 [reviewId]가 유일한 tie-breaker다 (규약 §5-3, TMT-178).
 */
data class HomeFeedKey(
    val distanceMeters: Int?,
    val createdAt: Instant?,
    val reviewId: Long,
)

data class HomeFeedResult(
    val items: List<ReviewCardView>,
    val hasNext: Boolean,
    /** 좌표 유무로 정렬이 갈리므로 다음 커서의 키 구성도 갈린다. */
    val sortedByDistance: Boolean,
) {
    val lastKey: HomeFeedKey?
        get() =
            items.lastOrNull()?.let {
                if (sortedByDistance) {
                    HomeFeedKey(requireNotNull(it.distanceMeters), null, it.reviewId)
                } else {
                    HomeFeedKey(null, it.createdAt, it.reviewId)
                }
            }
}

/** 큐레이션 칩 목록 (B §2-4) — 서버 상수다. 페이징하지 않는다. */
interface GetCurationTagsUseCase {
    fun get(): List<CurationTagView>
}

data class CurationTagView(
    val curationTagId: String,
    val label: String,
)
