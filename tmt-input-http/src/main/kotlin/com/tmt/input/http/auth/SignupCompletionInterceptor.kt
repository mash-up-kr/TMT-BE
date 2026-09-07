package com.tmt.input.http.auth

import com.tmt.application.port.input.CheckSignupCompletedUseCase
import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor

/**
 * 가입 화면을 끝내지 않은 사용자의 요청을 막는다 (TMT-370). 카카오 로그인만으로 만들어진 계정은
 * 닉네임이 카카오 값이라, 그대로 리뷰·그룹에 들어가면 사용자가 정하지 않은 이름이 남는다.
 *
 * 통과 경로는 [WebConfig][com.tmt.input.http.config.WebConfig]가 제외 패턴으로 정한다 —
 * 가입 완결과 내 프로필 조회는 미완료 상태에서도 부를 수 있어야 한다.
 * 토큰이 없는 요청은 여기서 판단하지 않는다 — 필수 여부는 [UserIdArgumentResolver] 몫이다.
 */
@Component
class SignupCompletionInterceptor(
    private val checkSignupCompletedUseCase: CheckSignupCompletedUseCase,
) : HandlerInterceptor {
    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
    ): Boolean {
        val userId = request.getAttribute(UserIdArgumentResolver.USER_ID_ATTRIBUTE) as? Long ?: return true
        if (!checkSignupCompletedUseCase.isCompleted(userId)) {
            throw TmtException(ErrorCode.SIGNUP_NOT_COMPLETED)
        }
        return true
    }
}
