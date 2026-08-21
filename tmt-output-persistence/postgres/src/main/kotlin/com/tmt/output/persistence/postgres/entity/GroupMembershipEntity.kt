package com.tmt.output.persistence.postgres.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

enum class GroupMembershipStatus {
    ACTIVE,
    LEFT,
}

/** 탈퇴해도 행을 지우지 않는다. ACTIVE인 (group_id, user_id)만 유일하다. */
@Entity
@Table(name = "group_membership")
class GroupMembershipEntity(
    @Column(nullable = false)
    val groupId: Long,
    @Column(nullable = false)
    val userId: Long,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @Enumerated(EnumType.STRING)
    @Column(length = 10, nullable = false)
    var status: GroupMembershipStatus = GroupMembershipStatus.ACTIVE
        protected set

    @Column(nullable = false)
    var joinedAt: Instant = Instant.now()
        protected set

    var leftAt: Instant? = null
        protected set
}
