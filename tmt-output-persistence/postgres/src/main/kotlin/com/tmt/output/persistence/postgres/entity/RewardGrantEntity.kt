package com.tmt.output.persistence.postgres.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

enum class RewardType {
    GROUP_JOIN_TICKET,
}

enum class RewardSourceType {
    SIGNUP,
    REVIEW,
}

@Entity
@Table(name = "reward_grant")
class RewardGrantEntity(
    @Column(nullable = false)
    val userId: Long,
    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    val rewardType: RewardType,
    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    val sourceType: RewardSourceType,
    /** SIGNUP이면 userId, REVIEW면 reviewId. (sourceType, sourceId, rewardType)이 유일하다. */
    @Column(nullable = false)
    val sourceId: Long,
) : BaseCreatedEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0
}
