package com.tmt.application.domain.user

import com.tmt.application.port.input.AttachMediaUseCase
import com.tmt.application.port.input.CheckSignupCompletedUseCase
import com.tmt.application.port.input.GetUserProfileUseCase
import com.tmt.application.port.input.UpdateUserProfileCommand
import com.tmt.application.port.input.UpdateUserProfileUseCase
import com.tmt.application.port.input.UserProfileView
import com.tmt.application.port.output.persistence.UserAccountPort
import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * 가입 완결·프로필 수정 (TMT-370). 카카오 로그인이 만든 행의 닉네임을 사용자 입력값으로 덮어쓰고,
 * 프로필 사진을 사용자가 올린 사진으로 바꾼다.
 */
@Service
class UserProfileService(
    private val userAccountPort: UserAccountPort,
    private val attachMediaUseCase: AttachMediaUseCase,
    private val getUserProfileUseCase: GetUserProfileUseCase,
) : UpdateUserProfileUseCase,
    CheckSignupCompletedUseCase {
    override fun isCompleted(userId: Long): Boolean = userAccountPort.findById(userId)?.profileCompletedAt != null

    @Transactional
    override fun update(command: UpdateUserProfileCommand): UserProfileView {
        val nickname = command.nickname.trim()
        validateNickname(nickname)

        val current = userAccountPort.findById(command.userId) ?: throw TmtException(ErrorCode.USER_NOT_FOUND)
        val newAssetId = command.profileImageAssetId
        if (newAssetId != null && newAssetId != current.profileImageAssetId) {
            attachMediaUseCase.verifyAttachable(command.userId, listOf(newAssetId))
        }

        userAccountPort.updateProfile(
            userId = command.userId,
            nickname = nickname,
            profileImageAssetId = newAssetId,
            completedAt = current.profileCompletedAt ?: Instant.now(),
        ) ?: throw TmtException(ErrorCode.USER_NOT_FOUND)

        // 사진을 바꿨을 때만 전이한다 — 그대로 둔 수정에서 이미 ATTACHED인 사진을 다시 붙이면
        // MEDIA_ALREADY_ATTACHED가 난다 (그룹 대표 이미지와 같은 처리, M7)
        if (newAssetId != current.profileImageAssetId) {
            current.profileImageAssetId?.let { attachMediaUseCase.detach(listOf(it)) }
            newAssetId?.let { attachMediaUseCase.attach(listOf(it)) }
        }

        return getUserProfileUseCase.getMine(command.userId)
    }

    /** U3: 2~20자. 코드포인트로 센다 — DB CHECK(users_nickname_len)가 char_length 기준이다 */
    private fun validateNickname(nickname: String) {
        val length = nickname.codePointCount(0, nickname.length)
        if (length !in NICKNAME_MIN..NICKNAME_MAX) {
            throw TmtException(ErrorCode.VALIDATION_FAILED, "닉네임은 ${NICKNAME_MIN}~${NICKNAME_MAX}자여야 합니다.")
        }
    }

    companion object {
        private const val NICKNAME_MIN = 2
        private const val NICKNAME_MAX = 20
    }
}
