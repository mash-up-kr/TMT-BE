package com.tmt.common.exception

enum class ErrorType {
    /** 요청만 보고 판단되는 실패. 저장된 상태를 조회해야 하면 [UNPROCESSABLE] */
    VALIDATION,
    UNAUTHORIZED,
    FORBIDDEN,
    NOT_FOUND,
    CONFLICT,
    GONE,
    UNPROCESSABLE,
    RATE_LIMITED,

    /** 외부 의존(주소 API 등)이 응답하지 못함. 우리 서버 결함인 [INTERNAL]과 구분한다 */
    EXTERNAL_UNAVAILABLE,
    INTERNAL,
}
