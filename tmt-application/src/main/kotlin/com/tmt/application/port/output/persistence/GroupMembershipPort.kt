package com.tmt.application.port.output.persistence

import java.time.Instant

/** 그룹 가입·탈퇴 (H §2·§3-3, TMT-227). 탈퇴는 행을 지우지 않고 `LEFT`로 바꾼다. */
interface GroupMembershipPort {
    /** 가입 팝업·가입·탈퇴가 보는 그룹 — 없으면 null (GROUP_NOT_FOUND). */
    fun findJoinTarget(groupId: Long): GroupJoinTarget?

    /**
     * ACTIVE 멤버십을 만든다. 이미 ACTIVE면 false — `membership_active_uq`가 심판이라
     * 동시 가입 둘 중 하나만 true를 받는다 (D5).
     */
    fun join(
        groupId: Long,
        userId: Long,
        joinedAt: Instant,
    ): Boolean

    /** ACTIVE → LEFT 조건부 전이. 전이했으면 true, ACTIVE 행이 없으면 false. */
    fun leave(
        groupId: Long,
        userId: Long,
    ): Boolean

    /**
     * ACTIVE 멤버십을 **행을 잠근 채** 확인한다 (TMT-351). 공유 교체가 탈퇴와 겹칠 때의 상호배제다 —
     * 탈퇴의 ACTIVE→LEFT 전이가 같은 행을 갱신하므로, 잠금을 쥔 쪽이 커밋할 때까지 상대가 기다린다.
     * 잠그지 않은 [com.tmt.application.port.output.persistence.GroupReviewQueryPort.isMember]로 가르면
     * 통과 직후 탈퇴가 커밋해 LEFT인데 공유가 남는다 (G10).
     */
    fun lockActiveMembership(
        groupId: Long,
        userId: Long,
    ): Boolean
}

data class GroupJoinTarget(
    val groupId: Long,
    val name: String,
    val imageS3Key: String?,
    val ownerId: Long,
)
