package com.tmt.input.http.config

import com.tmt.common.exception.ErrorCode

/**
 * 이 엔드포인트에서만 나는 실패 코드를 스펙에 싣는다. HTTP 상태는 [ErrorCode]의 ErrorType이 정한다.
 *
 * 인증(401)·요청 형식(400)·커서(400)·멱등(409)·서버 오류(500)는 핸들러 모양만 보고 알 수 있어
 * [ErrorResponseCustomizer]가 자동으로 붙이므로 여기 적지 않는다.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class ApiErrorCodes(
    vararg val value: ErrorCode,
)
