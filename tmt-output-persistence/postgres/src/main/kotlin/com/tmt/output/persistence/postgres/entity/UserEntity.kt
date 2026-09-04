package com.tmt.output.persistence.postgres.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "users")
class UserEntity(
    @Column(nullable = false, unique = true)
    val kakaoId: Long,
    /** 2~20자, 중복 허용 (U3). 폭·CHECK의 정본은 V6 마이그레이션이다 */
    @Column(length = 20, nullable = false)
    var nickname: String,
    @Column(columnDefinition = "text")
    var profileImageUrl: String? = null,
) : BaseTimeEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0
}
