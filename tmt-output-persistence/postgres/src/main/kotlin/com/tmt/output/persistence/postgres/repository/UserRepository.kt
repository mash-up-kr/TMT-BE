package com.tmt.output.persistence.postgres.repository

import com.tmt.output.persistence.postgres.entity.UserEntity
import org.springframework.data.jpa.repository.JpaRepository

interface UserRepository : JpaRepository<UserEntity, Long> {
    fun findByKakaoId(kakaoId: Long): UserEntity?
}
