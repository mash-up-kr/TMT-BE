package com.tmt.output.persistence.postgres.repository

import com.tmt.output.persistence.postgres.entity.CurationTagEntity
import org.springframework.data.jpa.repository.JpaRepository

/**
 * 큐레이션 칩 taxonomy (E12). 칩에 속한 매장은 여기서 읽지 않는다 —
 * 검색·핀 쿼리가 `curation_tag_place`를 직접 술어로 건다. 목록을 애플리케이션으로
 * 끌어올려 `IN`으로 넘기면 목록 크기에 비례해 파라미터가 늘고 상한이 없다.
 */
interface CurationTagRepository : JpaRepository<CurationTagEntity, String> {
    fun findAllByActiveIsTrueOrderByDisplayOrderAsc(): List<CurationTagEntity>

    fun existsByIdAndActiveIsTrue(id: String): Boolean
}
