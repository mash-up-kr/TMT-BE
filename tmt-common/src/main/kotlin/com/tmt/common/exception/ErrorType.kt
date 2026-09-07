package com.tmt.common.exception

/**
 * 응답 상태 코드를 가르는 근거다. **로그 레벨의 근거로 쓰지 않는다** — 502와 500은 클라이언트에게
 * 다른 상태지만 사용자에게는 둘 다 "안 됨"이라, 5xx는 원인이 밖에 있어도 알려야 한다.
 * 레벨 기준은 docs/LOGGING.md에 있다 (TMT-354).
 */
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

    /**
     * 외부 의존(주소 API 등)이 응답하지 못함. 우리 서버 결함인 [INTERNAL]과 구분한다.
     * 구분의 목적은 재시도 가능 여부를 알리는 것이지, 알림을 줄이는 것이 아니다
     */
    EXTERNAL_UNAVAILABLE,

    /** 기능이 일시적으로 불가. 재시도하면 되는 실패라 [EXTERNAL_UNAVAILABLE](502)과 상태가 다르다 */
    SERVICE_UNAVAILABLE,
    INTERNAL,
}
