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
    INTERNAL,
}
