package com.tmt.input.http.mock

import java.util.concurrent.ConcurrentHashMap

/**
 * 등록된 mock 사용자. 타인 프로필(J-01)이 없는 사용자에 404를 내려면 존재하는 집합이 필요하다.
 * UT 대상자 계정은 X-User-Id 1~4이고, 999는 시드 리뷰·그룹의 작성자다.
 */
class MockUserStore(
    seeds: List<MockUser>,
) {
    private val users = ConcurrentHashMap<Long, MockUser>()

    init {
        seeds.forEach { users[it.userId] = it }
    }

    fun find(userId: Long): MockUser? = users[userId]

    /**
     * 리뷰 카드의 작성자. 등록되지 않은 X-User-Id로도 리뷰를 쓸 수 있으므로 이름을 만들어 준다 —
     * 프로필 조회(404)와 달리 카드에서는 작성자가 비어 있으면 안 된다.
     */
    fun authorOf(userId: Long): MockUser = users[userId] ?: MockUser(userId, "미식가$userId", email = null)
}
