package com.tmt.output.persistence.postgres.adapter

import com.tmt.application.port.output.persistence.UserAccount
import com.tmt.application.port.output.persistence.UserAccountPort
import com.tmt.output.persistence.postgres.entity.UserEntity
import com.tmt.output.persistence.postgres.repository.UserRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Component
class UserAccountAdapter(
    private val userRepository: UserRepository,
) : UserAccountPort {
    override fun findByKakaoId(kakaoId: Long): UserAccount? = userRepository.findByKakaoId(kakaoId)?.toAccount()

    override fun findById(userId: Long): UserAccount? = userRepository.findById(userId).orElse(null)?.toAccount()

    override fun create(
        kakaoId: Long,
        nickname: String,
    ): UserAccount? =
        try {
            userRepository.save(UserEntity(kakaoId = kakaoId, nickname = nickname)).toAccount()
        } catch (e: DataIntegrityViolationException) {
            // 같은 kakao_id가 먼저 들어갔다 (U1 UNIQUE) — 동시 로그인 경쟁은 호출자가 재조회로 푼다
            null
        }

    @Transactional
    override fun updateProfile(
        userId: Long,
        nickname: String,
        profileImageAssetId: Long?,
        completedAt: Instant,
    ): UserAccount? {
        val user = userRepository.findById(userId).orElse(null) ?: return null
        user.nickname = nickname
        user.profileImageAssetId = profileImageAssetId
        // 카카오 값이 남아 있으면 지운다 — 사용자가 고른 사진만 프로필에 남는다 (TMT-370)
        user.profileImageUrl = null
        user.profileCompletedAt = completedAt
        return user.toAccount()
    }

    @Transactional
    override fun invalidateTokensIssuedBefore(
        userId: Long,
        at: Instant,
    ): Boolean {
        val user = userRepository.findById(userId).orElse(null) ?: return false
        user.tokensInvalidBefore = at
        return true
    }

    private fun UserEntity.toAccount() =
        UserAccount(
            id = id,
            kakaoId = kakaoId,
            nickname = nickname,
            profileImageUrl = profileImageUrl,
            profileImageAssetId = profileImageAssetId,
            profileCompletedAt = profileCompletedAt,
            tokensInvalidBefore = tokensInvalidBefore,
        )
}
