package com.tmt.common.exception

/**
 * enum 이름이 그대로 응답의 `code` 값이 되고, 클라이언트 분기의 기준이 된다.
 * 값이 바뀌면 파괴적 변경이므로 이름을 고치는 대신 새 코드를 추가한다.
 * 도메인 코드는 `<도메인>_<사유>` 형태로 짓는다.
 */
enum class ErrorCode(
    val errorType: ErrorType,
    val defaultMessage: String,
) {
    // 공통
    VALIDATION_FAILED(ErrorType.VALIDATION, "요청 형식이 잘못되었습니다."),
    INVALID_CURSOR(ErrorType.VALIDATION, "잘못된 커서입니다."),
    UNAUTHORIZED(ErrorType.UNAUTHORIZED, "로그인이 필요합니다."),
    FORBIDDEN(ErrorType.FORBIDDEN, "접근 권한이 없습니다."),
    RESOURCE_NOT_FOUND(ErrorType.NOT_FOUND, "요청 경로가 잘못되었습니다."),
    INTERNAL_ERROR(ErrorType.INTERNAL, "서버 내부 오류가 발생했습니다."),
    INTERNAL_ERROR_TEST(ErrorType.INTERNAL, "에러 테스트용 예외입니다."),
}
