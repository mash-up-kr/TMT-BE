package com.tmt.common.exception

/**
 * 티켓이 모자라 거절된 요청. 응답에 화면 갱신용 티켓 상태를 함께 싣는다 (공통 규약 §3-2) —
 * 리뷰 삭제(I §6-4)와 그룹 가입(H §2-2)이 같은 형태를 쓴다.
 *
 * [TmtException]과 따로 두는 이유는 본문에 `ticket` 객체가 더 붙기 때문이다. 코드·상태·제목은
 * [errorCode]에서 그대로 온다.
 */
class TicketShortageException(
    val errorCode: ErrorCode,
    val availableCount: Int,
    val requiredCount: Int = 1,
) : RuntimeException("[${errorCode.name}] ${errorCode.defaultMessage}") {
    val shortageCount: Int get() = requiredCount - availableCount
}
