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
    IDEMPOTENCY_CONFLICT(ErrorType.CONFLICT, "같은 멱등성 키로 다른 요청이 이미 처리되었습니다."),

    // 매장
    PLACE_NOT_FOUND(ErrorType.NOT_FOUND, "매장을 찾을 수 없습니다."),
    PLACE_CATEGORY_NOT_FOUND(ErrorType.VALIDATION, "음식 카테고리가 목록에 없습니다."),
    ADDRESS_NOT_FOUND(ErrorType.NOT_FOUND, "주소를 찾을 수 없습니다."),

    // 미디어
    MEDIA_FILE_TOO_LARGE(ErrorType.VALIDATION, "파일이 허용 크기를 초과했습니다."),
    MEDIA_CONTENT_TYPE_NOT_ALLOWED(ErrorType.VALIDATION, "허용되지 않는 파일 형식입니다."),
    MEDIA_NOT_OWNED(ErrorType.FORBIDDEN, "본인이 발급받은 사진만 사용할 수 있습니다."),
    MEDIA_ALREADY_ATTACHED(ErrorType.CONFLICT, "이미 다른 저장에 사용된 사진입니다."),

    // 저장·리뷰
    SAVE_NOT_FOUND(ErrorType.NOT_FOUND, "저장을 찾을 수 없습니다."),
    SAVE_ALREADY_REVIEWED(ErrorType.CONFLICT, "이미 리뷰가 완성된 저장은 수정할 수 없습니다."),
    SAVE_PLACE_IMMUTABLE(ErrorType.UNPROCESSABLE, "저장의 매장은 변경할 수 없습니다."),
    REVIEW_CONTENT_TOO_LONG(ErrorType.VALIDATION, "본문이 최대 길이를 초과했습니다."),
    REVIEW_TAG_NOT_FOUND(ErrorType.VALIDATION, "정의되지 않은 태그입니다."),
    REVIEW_NOT_FOUND(ErrorType.NOT_FOUND, "리뷰를 찾을 수 없습니다."),
    REVIEW_DELETE_TICKET_REQUIRED(ErrorType.CONFLICT, "리뷰를 삭제하려면 티켓 1장이 필요합니다."),

    // 그룹
    GROUP_NOT_FOUND(ErrorType.NOT_FOUND, "그룹을 찾을 수 없습니다."),
    GROUP_TAG_NOT_FOUND(ErrorType.VALIDATION, "정의되지 않은 카테고리·지역 값입니다."),
    GROUP_NAME_DUPLICATED(ErrorType.CONFLICT, "같은 이름의 그룹이 있습니다."),
    GROUP_OWNER_REQUIRED(ErrorType.FORBIDDEN, "그룹 생성자만 할 수 있습니다."),
    GROUP_OWNER_CANNOT_LEAVE(ErrorType.UNPROCESSABLE, "그룹장은 탈퇴할 수 없습니다."),
    GROUP_MEMBERSHIP_REQUIRED(ErrorType.FORBIDDEN, "가입하지 않은 그룹입니다."),
    ALREADY_GROUP_MEMBER(ErrorType.CONFLICT, "이미 가입한 그룹입니다."),
    GROUP_JOIN_TICKET_REQUIRED(ErrorType.CONFLICT, "그룹 가입에 필요한 티켓이 부족합니다."),
}
