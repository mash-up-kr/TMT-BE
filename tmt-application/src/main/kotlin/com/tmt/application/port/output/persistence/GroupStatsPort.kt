package com.tmt.application.port.output.persistence

/**
 * 그룹의 가입자 수·리뷰 수·매장 수를 갱신한다.
 *
 * 가입자 수는 조건부 UPDATE로 증감한다. 리뷰 수·매장 수는 공유 집합에서 파생되므로
 * 집합이 바뀐 뒤 다시 세는 편이 안전하다 — 공유 해제와 매장 중복이 얽혀 증감만으로는 어긋난다.
 */
interface GroupStatsPort {
    fun addMember(groupId: Long)

    fun removeMember(groupId: Long)

    /** 공유 집합이 바뀐 뒤 `review_count`·`place_count`를 다시 맞춘다. */
    fun refreshShareStats(groupId: Long)
}
