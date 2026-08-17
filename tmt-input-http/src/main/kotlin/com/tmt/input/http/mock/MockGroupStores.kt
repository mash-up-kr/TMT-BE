package com.tmt.input.http.mock

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/** 그룹 멤버십 — 가입 시각을 함께 든다. 생성자는 생성 시점에 자동 가입된다 (티켓 차감 없음). */
class MockMembershipStore {
    private val membersByGroup = ConcurrentHashMap<String, MutableMap<Long, Instant>>()

    fun join(
        groupId: String,
        userId: Long,
        at: Instant,
    ) {
        membersByGroup.computeIfAbsent(groupId) { ConcurrentHashMap() }[userId] = at
    }

    fun leave(
        groupId: String,
        userId: Long,
    ) {
        membersByGroup[groupId]?.remove(userId)
    }

    fun isMember(
        groupId: String,
        userId: Long?,
    ): Boolean = userId != null && membersByGroup[groupId]?.containsKey(userId) == true

    fun memberCount(groupId: String): Int = membersByGroup[groupId]?.size ?: 0
}

/** 그룹 리뷰 공유 — 사용자별 집합으로 관리한다 (G14). PUT 전체 교체의 단위가 (그룹, 사용자)다. */
class MockReviewShareStore {
    private val sharesByGroup = ConcurrentHashMap<String, ConcurrentHashMap<Long, LinkedHashSet<String>>>()

    fun replace(
        groupId: String,
        userId: Long,
        reviewIds: List<String>,
    ) {
        sharesByGroup.computeIfAbsent(groupId) { ConcurrentHashMap() }[userId] = LinkedHashSet(reviewIds)
    }

    fun add(
        groupId: String,
        userId: Long,
        reviewId: String,
    ) {
        sharesByGroup
            .computeIfAbsent(groupId) { ConcurrentHashMap() }
            .computeIfAbsent(userId) { LinkedHashSet() }
            .add(reviewId)
    }

    fun userShares(
        groupId: String,
        userId: Long,
    ): Set<String> = sharesByGroup[groupId]?.get(userId)?.toSet() ?: emptySet()

    /** 그룹에 공유된 전체 리뷰 (멤버 전원의 합집합) — 집계·커버·게이트 목록의 원천. */
    fun allShares(groupId: String): Set<String> = sharesByGroup[groupId]?.values?.flatten()?.toSet() ?: emptySet()

    /** 탈퇴하면 그 그룹에 공유했던 내 리뷰가 전부 내려간다 (G10). */
    fun removeUser(
        groupId: String,
        userId: Long,
    ) {
        sharesByGroup[groupId]?.remove(userId)
    }
}
