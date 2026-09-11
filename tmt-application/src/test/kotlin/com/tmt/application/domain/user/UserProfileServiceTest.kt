package com.tmt.application.domain.user

import com.tmt.application.port.input.AttachMediaUseCase
import com.tmt.application.port.input.GetUserProfileUseCase
import com.tmt.application.port.input.ImageAssetSelection
import com.tmt.application.port.input.UpdateUserProfileCommand
import com.tmt.application.port.input.UserProfileView
import com.tmt.application.port.output.persistence.UserAccount
import com.tmt.application.port.output.persistence.UserAccountPort
import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UserProfileServiceTest {
    private val userPort = FakeUserAccountPort()
    private val attachMedia = RecordingAttachMedia()
    private val service = UserProfileService(userPort, attachMedia, StubGetUserProfile())

    @Test
    fun `가입 완결은 닉네임을 덮어쓰고 완료 시각을 채운다`() {
        userPort.accounts += account(id = 7L, nickname = "카카오닉")

        service.update(
            UpdateUserProfileCommand(userId = 7L, nickname = "준형이", profileImage = ImageAssetSelection.Set(12L)),
        )

        val saved = userPort.accounts.single()
        assertEquals("준형이", saved.nickname)
        assertEquals(12L, saved.profileImageAssetId)
        assertNotNull(saved.profileCompletedAt)
    }

    @Test
    fun `사진에 null을 실으면 사진 없는 상태가 된다`() {
        userPort.accounts += account(id = 7L, nickname = "카카오닉")

        service.update(UpdateUserProfileCommand(userId = 7L, nickname = "준형이", profileImage = ImageAssetSelection.None))

        assertNull(userPort.accounts.single().profileImageAssetId)
        assertEquals(emptyList(), attachMedia.attachedIds)
    }

    @Test
    fun `사진 필드를 생략한 수정은 현재 사진을 유지한다 (TMT-415)`() {
        userPort.accounts += account(id = 7L, nickname = "준형이", profileImageAssetId = 3L)

        service.update(UpdateUserProfileCommand(userId = 7L, nickname = "새이름", profileImage = ImageAssetSelection.Keep))

        assertEquals(3L, userPort.accounts.single().profileImageAssetId)
        assertEquals(emptyList(), attachMedia.attachedIds)
        assertEquals(emptyList(), attachMedia.detachedIds)
    }

    @Test
    fun `사진을 바꾸면 새 사진을 붙이고 이전 사진은 떼어 낸다`() {
        userPort.accounts += account(id = 7L, nickname = "준형이", profileImageAssetId = 3L)

        service.update(
            UpdateUserProfileCommand(userId = 7L, nickname = "준형이", profileImage = ImageAssetSelection.Set(9L)),
        )

        assertEquals(listOf(9L), attachMedia.attachedIds)
        assertEquals(listOf(3L), attachMedia.detachedIds)
        assertEquals(listOf(7L to listOf(9L)), attachMedia.verifyCalls)
    }

    @Test
    fun `사진을 그대로 둔 수정은 다시 붙이지 않는다`() {
        // 이미 ATTACHED라 재부착하면 MEDIA_ALREADY_ATTACHED가 난다
        userPort.accounts += account(id = 7L, nickname = "준형이", profileImageAssetId = 3L)

        service.update(
            UpdateUserProfileCommand(userId = 7L, nickname = "새이름", profileImage = ImageAssetSelection.Set(3L)),
        )

        assertEquals(emptyList(), attachMedia.attachedIds)
        assertEquals(emptyList(), attachMedia.detachedIds)
        assertEquals(emptyList(), attachMedia.verifyCalls)
    }

    @Test
    fun `이미 가입을 끝냈으면 완료 시각을 새로 찍지 않는다`() {
        val completedAt = Instant.parse("2026-09-01T00:00:00Z")
        userPort.accounts += account(id = 7L, nickname = "준형이", profileCompletedAt = completedAt)

        service.update(UpdateUserProfileCommand(userId = 7L, nickname = "새이름", profileImage = ImageAssetSelection.None))

        assertEquals(completedAt, userPort.accounts.single().profileCompletedAt)
    }

    @Test
    fun `닉네임이 2자 미만이거나 20자를 넘으면 VALIDATION_FAILED다`() {
        userPort.accounts += account(id = 7L, nickname = "준형이")

        listOf("가", "가".repeat(21)).forEach { nickname ->
            val error =
                assertFailsWith<TmtException> {
                    service.update(UpdateUserProfileCommand(7L, nickname, ImageAssetSelection.None))
                }
            assertEquals(ErrorCode.VALIDATION_FAILED, error.errorCode)
        }
    }

    @Test
    fun `닉네임 길이는 코드포인트로 센다`() {
        // 이모지는 UTF-16 코드 유닛으로 2다 — length로 재면 20자 상한이 이모지 10개에서 걸린다
        userPort.accounts += account(id = 7L, nickname = "준형이")

        service.update(UpdateUserProfileCommand(7L, "🍅".repeat(20), ImageAssetSelection.None))

        assertEquals("🍅".repeat(20), userPort.accounts.single().nickname)
    }

    @Test
    fun `없는 사용자는 USER_NOT_FOUND다`() {
        val error =
            assertFailsWith<TmtException> {
                service.update(UpdateUserProfileCommand(404L, "준형이", ImageAssetSelection.None))
            }
        assertEquals(ErrorCode.USER_NOT_FOUND, error.errorCode)
    }

    @Test
    fun `완료 시각이 있으면 가입을 끝낸 것이다`() {
        userPort.accounts += account(id = 7L, nickname = "준형이", profileCompletedAt = Instant.now())
        userPort.accounts += account(id = 8L, nickname = "카카오닉")

        assertTrue(service.isCompleted(7L))
        assertEquals(false, service.isCompleted(8L))
    }

    @Test
    fun `탈퇴해 사라진 사용자의 토큰은 가입 미완료가 아니라 401이다 (TMT-409)`() {
        val error = assertFailsWith<TmtException> { service.isCompleted(404L) }

        assertEquals(ErrorCode.UNAUTHORIZED, error.errorCode)
    }

    private fun account(
        id: Long,
        nickname: String,
        profileImageAssetId: Long? = null,
        profileCompletedAt: Instant? = null,
    ) = UserAccount(
        id = id,
        kakaoId = id * 100,
        nickname = nickname,
        profileImageUrl = null,
        profileImageAssetId = profileImageAssetId,
        profileCompletedAt = profileCompletedAt,
        tokensInvalidBefore = null,
    )

    private class FakeUserAccountPort : UserAccountPort {
        val accounts = mutableListOf<UserAccount>()

        override fun findByKakaoId(kakaoId: Long): UserAccount? = accounts.firstOrNull { it.kakaoId == kakaoId }

        override fun findById(userId: Long): UserAccount? = accounts.firstOrNull { it.id == userId }

        override fun create(
            kakaoId: Long,
            nickname: String,
        ): UserAccount? = null

        override fun invalidateTokensIssuedBefore(
            userId: Long,
            at: Instant,
        ): Boolean = accounts.any { it.id == userId }

        override fun updateProfile(
            userId: Long,
            nickname: String,
            profileImageAssetId: Long?,
            completedAt: Instant,
        ): UserAccount? {
            val index = accounts.indexOfFirst { it.id == userId }
            if (index < 0) return null
            val updated =
                accounts[index].copy(
                    nickname = nickname,
                    profileImageUrl = null,
                    profileImageAssetId = profileImageAssetId,
                    profileCompletedAt = completedAt,
                )
            accounts[index] = updated
            return updated
        }
    }

    private class RecordingAttachMedia : AttachMediaUseCase {
        val verifyCalls = mutableListOf<Pair<Long, List<Long>>>()
        val attachedIds = mutableListOf<Long>()
        val detachedIds = mutableListOf<Long>()

        override fun verifyAttachable(
            ownerId: Long,
            assetIds: List<Long>,
            reattachableIds: Set<Long>,
        ) {
            verifyCalls += ownerId to assetIds
        }

        override fun attach(
            assetIds: List<Long>,
            reattachableIds: Set<Long>,
        ) {
            attachedIds += assetIds
        }

        override fun detach(assetIds: List<Long>) {
            detachedIds += assetIds
        }
    }

    private class StubGetUserProfile : GetUserProfileUseCase {
        override fun getMine(userId: Long) = view(userId)

        override fun getOther(targetUserId: Long) = view(targetUserId)

        private fun view(userId: Long) =
            UserProfileView(
                userId = userId,
                nickname = "준형이",
                profileImageUrl = null,
                reviewCount = 0,
                joinedGroupCount = 0,
                favoritePlaceCount = 0,
                availableTicketCount = 1,
                email = null,
                profileCompleted = true,
            )
    }
}
