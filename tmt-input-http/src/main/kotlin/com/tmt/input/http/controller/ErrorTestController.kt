package com.tmt.input.http.controller

import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException
import com.tmt.input.http.config.ApiErrorCodes
import org.springframework.context.annotation.Profile
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 에러 수집 경로를 일부러 태워보는 자리 (TMT-325). **prod에서는 등록되지 않는다** — 인증이 없어
 * 누구나 부를 수 있고, 부른 만큼 500이 Sentry 이벤트로 쌓여 온콜 봇이 그때마다 분석에 들어간다.
 *
 * 컨트롤러를 따로 뺀 이유는 [HealthCheckController]가 prod에서도 떠 있어야 하기 때문이다.
 */
@Profile("!prod")
@RestController
@RequestMapping("/health")
class ErrorTestController {
    @PostMapping("/error-test-global")
    fun errorTestGlobal(): Nothing = throw RuntimeException()

    @ApiErrorCodes(ErrorCode.INTERNAL_ERROR_TEST)
    @PostMapping("/error-test-tmt")
    fun errorTestTmt(): Nothing =
        throw TmtException(
            ErrorCode.INTERNAL_ERROR_TEST,
            "error-test: 의도적으로 발생시킨 TmtException",
        )
}
