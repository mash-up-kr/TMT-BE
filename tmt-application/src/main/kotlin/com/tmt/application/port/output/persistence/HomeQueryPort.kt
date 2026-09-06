package com.tmt.application.port.output.persistence

import java.time.Instant

/**
 * 홈 읽기 (TMT-230). 그룹 테이블을 읽지만 그룹 실구현(TMT-220~223)과 파일을 나눠 둔다 —
 * 홈이 필요로 하는 모양(내 그룹 가입순·피드 키셋)이 그룹 목록과 달라서다.
 * **추천 캐러셀은 여기 없다** — 그룹 탐색의 추천순과 같은 목록이라 GroupExplorePort가 준다 (TMT-305).
 * 키셋·집계는 SQL이, 카드 조립은 서비스가 한다.
 */
interface HomeQueryPort {
    fun findNickname(userId: Long): String?

    /** 가입 순서(오래된 순) — A §2 */
    fun findMyGroups(userId: Long): List<MyGroupRow>

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

data class HomeFeedRows(
    val rows: List<ReviewCardRow>,
    val hasNext: Boolean,
)
