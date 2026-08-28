package com.tmt.application.domain.address

import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException

/**
 * juso는 특수문자·SQL 예약어가 섞인 검색어를 SQL Injection으로 보고 호출한 IP를 차단한다.
 * 차단은 재시도로 풀리지 않고 유선 신청으로만 해제되므로, 외부에 나가기 전에 두 단계로 거른다 (F §2-3).
 *
 * 1. 문자 화이트리스트 — 한글·영숫자·공백·하이픈만 통과. 하이픈은 지번(948-1)에 필요하다
 * 2. 예약어 토큰 제거 — 공백으로 쪼갠 뒤 **완전 일치**하는 토큰만 뺀다.
 *    부분 문자열로 맞추면 ORIGIN·UNIONMALL 같은 정상 검색어가 깨진다
 *
 * 두 단계가 다 필요하다 — OR·UNION·INSERT는 순수 영문자라 1단계를 그대로 통과한다.
 * 정제는 멱등이라 어댑터 진입점에서 다시 적용해도 결과가 같다.
 */
object AddressQuerySanitizer {
    const val MIN_LENGTH = 2

    private val DISALLOWED = Regex("[^가-힣ㄱ-ㅎㅏ-ㅣa-zA-Z0-9\\s-]")
    private val WHITESPACE = Regex("\\s+")

    /**
     * juso 문서가 명시한 것(OR·INSERT·UNION)보다 넓다. 틀린 방향의 비용이 비대칭이라 —
     * 과하게 걸러야 검색 하나가 실패하고, 덜 걸러면 IP가 차단된다 — 실제 차단 규칙을 확인할 때까지 넓게 둔다.
     */
    private val SQL_KEYWORDS =
        setOf(
            "OR",
            "AND",
            "NOT",
            "UNION",
            "SELECT",
            "INSERT",
            "UPDATE",
            "DELETE",
            "DROP",
            "ALTER",
            "CREATE",
            "FROM",
            "WHERE",
            "JOIN",
            "EXEC",
            "DECLARE",
        )

    fun sanitize(raw: String?): String =
        raw
            .orEmpty()
            .replace(DISALLOWED, " ")
            .split(WHITESPACE)
            .filter { it.isNotBlank() && it.uppercase() !in SQL_KEYWORDS }
            .joinToString(" ")

    /** 정제 후 2자 미만이면 외부를 부르지 않고 끊는다 */
    fun sanitizeOrThrow(raw: String?): String {
        val sanitized = sanitize(raw)
        if (sanitized.length < MIN_LENGTH) {
            throw TmtException(ErrorCode.VALIDATION_FAILED, "query는 정제 후 ${MIN_LENGTH}자 이상이어야 합니다.")
        }
        return sanitized
    }
}
