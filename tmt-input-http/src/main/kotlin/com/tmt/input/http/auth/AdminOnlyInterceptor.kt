package com.tmt.input.http.auth

import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor

/**
 * 어드민 경로를 막는다 (TMT-421). 판정은 [AdminAllowlist]이고, 적용 범위는
 * [WebConfig][com.tmt.input.http.config.WebConfig]가 `/v1/admin` 아래 경로 패턴으로 정한다.
 *
 * **컨트롤러나 서비스가 아니라 경로로 막는 이유** — 어드민 엔드포인트가 늘어날 때
 * 가드를 호출하는 것을 잊을 수 있다. 접두 하나로 묶으면 `/v1/admin` 아래에 새로 만든
 * 엔드포인트가 자동으로 막힌다. 빠뜨려서 열리는 쪽이 빠뜨려서 닫히는 쪽보다 위험하다.
 *
 * 토큰이 없으면 여기서 401을 내린다 — [UserIdArgumentResolver]는 핸들러 인자를 만들 때
 * 도는데, 그때는 이미 컨트롤러 안이라 늦다.
 */
@Component
class AdminOnlyInterceptor(
    private val adminAllowlist: AdminAllowlist,
) : HandlerInterceptor {
    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
    ): Boolean {
        val userId =
            request.getAttribute(UserIdArgumentResolver.USER_ID_ATTRIBUTE) as? Long
                ?: throw TmtException(ErrorCode.UNAUTHORIZED)
        if (!adminAllowlist.isAdmin(userId)) {
            throw TmtException(ErrorCode.ADMIN_REQUIRED)
        }
        return true
    }
}
