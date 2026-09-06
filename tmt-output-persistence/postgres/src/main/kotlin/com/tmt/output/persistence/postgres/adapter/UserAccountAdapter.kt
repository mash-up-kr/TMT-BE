package com.tmt.output.persistence.postgres.adapter

import com.tmt.application.port.output.persistence.UserAccount
import com.tmt.application.port.output.persistence.UserAccountPort
import com.tmt.output.persistence.postgres.entity.UserEntity
import com.tmt.output.persistence.postgres.repository.UserRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component

@Component
class UserAccountAdapter(
    private val userRepository: UserRepository,
) : UserAccountPort {
    override fun findByKakaoId(kakaoId: Long): UserAccount? = userRepository.findByKakaoId(kakaoId)?.toAccount()

    override fun create(
        kakaoId: Long,
        nickname: String,
        profileImageUrl: String?,
    ): UserAccount? =
        try {
            userRepository
                .save(UserEntity(kakaoId = kakaoId, nickname = nickname, profileImageUrl = profileImageUrl))
                .toAccount()
        } catch (e: DataIntegrityViolationException) {
            // 같은 kakao_id가 먼저 들어갔다 (U1 UNIQUE) — 동시 로그인 경쟁은 호출자가 재조회로 푼다
            null
        }

    private fun UserEntity.toAccount() =
        UserAccount(
            id = id,
            kakaoId = kakaoId,
            nickname = nickname,
            profileImageUrl = profileImageUrl,
        )
}
