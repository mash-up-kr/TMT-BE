package com.tmt.application.port.output.persistence

import java.time.Instant

/**
 * 홈 읽기 (TMT-230). 그룹 테이블을 읽지만 그룹 실구현(TMT-220~223)과 파일을 나눠 둔다 —
 * 홈이 필요로 하는 모양(추천 상위 N·내 그룹 가입순)이 그룹 목록과 달라서다.
 * 키셋·집계는 SQL이, 카드 조립은 서비스가 한다.
 */
interface HomeQueryPort {
    fun findNickname(userId: Long): String?

    /** 가입 순서(오래된 순) — A §2 */
    fun findMyGroups(userId: Long): List<MyGroupRow>

    /**
     * 추천순(G17) 상위 [limit]개. **이미 가입한 그룹은 후보에서 뺀다** — 홈 캐러셀에만 적용되는
     * 규칙이고 그룹 탐색 목록에는 적용하지 않는다 (A §5-3).
     */
    fun findRecommendedGroups(
        userId: Long,
        limit: Int,
    ): List<GroupCardRow>

    /**
     * 가입한 그룹들에 공유된 리뷰를 거리 오름차순 (거리, reviewId) 키셋으로 읽는다.
     * 같은 리뷰가 여러 그룹에 공유돼 있어도 한 번만 나온다 (G19).
     */
    fun findFeedRowsByDistance(
        userId: Long,
        latitude: Double,
        longitude: Double,
        afterDistanceMeters: Int?,
        afterReviewId: Long?,
        limit: Int,
    ): HomeFeedRows

    /** 좌표가 없을 때의 대체 정렬 — (createdAt, reviewId) 내림차순 키셋 (A §3). */
    fun findFeedRowsByRecency(
        userId: Long,
        afterCreatedAt: Instant?,
        afterReviewId: Long?,
        limit: Int,
    ): HomeFeedRows
}

data class MyGroupRow(
    val groupId: Long,
    val name: String,
    /** 그룹 대표 이미지의 s3 키. 등록하지 않았으면 null */
    val imageS3Key: String?,
)

/** GroupCard 한 장의 행 — 커버는 공유 리뷰 사진에서 파생된다 (G16). */
data class GroupCardRow(
    val groupId: Long,
    val name: String,
    val oneLineDescription: String,
    val coverS3Key: String?,
    val memberCount: Int,
    val reviewCount: Int,
    val placeCount: Int,
    val matchedSavedPlaceCount: Int,
)

data class HomeFeedRows(
    val rows: List<ReviewCardRow>,
    val hasNext: Boolean,
)
