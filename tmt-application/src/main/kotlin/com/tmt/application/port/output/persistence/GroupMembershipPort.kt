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
}

data class GroupJoinTarget(
    val groupId: Long,
    val name: String,
    val imageS3Key: String?,
    val ownerId: Long,
)
