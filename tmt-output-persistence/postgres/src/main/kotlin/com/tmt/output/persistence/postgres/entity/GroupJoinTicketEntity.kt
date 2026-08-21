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

enum class GroupJoinTicketStatus {
    AVAILABLE,
    CONSUMED,
    REVOKED,
}

@Entity
@Table(name = "group_join_ticket")
class GroupJoinTicketEntity(
    @Column(nullable = false)
    val userId: Long,
    /** 발급 근거 1건당 1장. */
    @Column(nullable = false, unique = true)
    val rewardGrantId: Long,
) : BaseCreatedEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    /** 소비는 조건부 UPDATE로 한다 — 읽고 쓰면 같은 티켓이 두 번 소비된다. */
    @Enumerated(EnumType.STRING)
    @Column(length = 10, nullable = false)
    var status: GroupJoinTicketStatus = GroupJoinTicketStatus.AVAILABLE
        protected set

    /** 소비처 로그. FK를 걸지 않아 그룹이 사라져도 로그가 남는다. */
    var consumedGroupId: Long? = null
        protected set

    var consumedAt: Instant? = null
        protected set

    var revokedAt: Instant? = null
        protected set
}
