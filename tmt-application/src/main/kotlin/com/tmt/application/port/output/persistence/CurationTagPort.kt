package com.tmt.application.port.output.persistence

/**
 * 큐레이션 칩 (E12). 종전에는 서버 상수(`CurationPresets`)가 정본이었고,
 * 질문 36의 "운영이 매장을 수동 매핑" 갈래를 택하면서 테이블로 옮겼다.
 */
interface CurationTagPort {
    /** 화면 칩 배열 순서(display_order)로 활성 칩만 (B §2-4) */
    fun findActiveTags(): List<CurationTagRow>

    /**
     * 칩이 있고 활성인지. 없는·비활성 칩은 서비스가 빈 결과로 끊는다 —
     * 매장 목록 술어는 검색 쿼리가 직접 걸기 때문에 존재 여부만 필요하다.
     */
    fun existsActiveTag(curationTagId: String): Boolean
}

data class CurationTagRow(
    val curationTagId: String,
    val label: String,
)
