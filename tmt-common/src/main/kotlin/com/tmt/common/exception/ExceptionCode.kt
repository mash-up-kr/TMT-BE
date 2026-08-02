package com.tmt.common.exception

enum class ExceptionCode(
    val statusCode: Int,
    val defaultMessage: String,
) {
    // 400
    BAD_REQUEST_VALIDATION(400, "요청 형식이 잘못되었습니다."),

    // 404
    NOT_FOUND_RESOURCE(404, "요청 경로가 잘못되었습니다."),

    // 500
    INTERNAL_SERVER_ERROR(500, "서버 내부 오류가 발생했습니다."),
    INTERNAL_ERROR_TEST(500, "에러 테스트용 예외입니다."),
}
