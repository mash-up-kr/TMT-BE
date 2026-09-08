package com.tmt.input.http.auth

import com.tmt.application.port.input.CheckSignupCompletedUseCase
import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SignupCompletionInterceptorTest {
    @Test
    fun `가입을 끝내지 않은 사용자의 요청은 SIGNUP_NOT_COMPLETED다`() {
        val interceptor = SignupCompletionInterceptor(StubCheck(completed = false))
        val request = MockHttpServletRequest().apply { setAttribute(UserIdArgumentResolver.USER_ID_ATTRIBUTE, 7L) }

        val error =
            kotlin.runCatching { interceptor.preHandle(request, MockHttpServletResponse(), Any()) }.exceptionOrNull()

        assertEquals(ErrorCode.SIGNUP_NOT_COMPLETED, (error as TmtException).errorCode)
    }

    @Test
    fun `가입을 끝낸 사용자는 통과한다`() {
        val interceptor = SignupCompletionInterceptor(StubCheck(completed = true))
        val request = MockHttpServletRequest().apply { setAttribute(UserIdArgumentResolver.USER_ID_ATTRIBUTE, 7L) }

        assertTrue(interceptor.preHandle(request, MockHttpServletResponse(), Any()))
    }

    @Test
    fun `토큰이 없는 요청은 여기서 판단하지 않는다`() {
        // 인증 필수 여부는 UserIdArgumentResolver가 @UserId 선언으로 정한다 — 여기서 막으면 401이 403이 된다
        val check = StubCheck(completed = false)
        val interceptor = SignupCompletionInterceptor(check)

        assertTrue(interceptor.preHandle(MockHttpServletRequest(), MockHttpServletResponse(), Any()))
        assertEquals(emptyList(), check.calls)
    }

    private class StubCheck(
        private val completed: Boolean,
    ) : CheckSignupCompletedUseCase {
        val calls = mutableListOf<Long>()

        override fun isCompleted(userId: Long): Boolean {
            calls += userId
            return completed
        }
    }
}
