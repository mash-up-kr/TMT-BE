package com.tmt.output.persistence.postgres.entity

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.io.Serializable

/**
 * 큐레이션 칩 (E12). `review_tag_definition`과 같은 taxonomy 형태다 —
 * 삭제 대신 [active]로 내린다. 종전에는 서버 상수(`CurationPresets`)였고,
 * 질문 36의 "운영이 매장을 수동 매핑" 갈래를 택하면서 테이블이 됐다 (V9).
 */
@Entity
@Table(name = "curation_tag")
class CurationTagEntity(
    /** 'curation_euljiro_yajang' — API의 curationTagId 그대로다. 서버가 부여하지 않는다. */
    @Id
    @Column(length = 30)
    val id: String,
    @Column(length = 30, nullable = false)
    var label: String,
    @Column(nullable = false)
    var displayOrder: Short,
    @Column(nullable = false)
    var active: Boolean = true,
) : BaseTimeEntity()

@Embeddable
data class CurationTagPlaceId(
    @Column(length = 30, nullable = false)
    val curationTagId: String,
    @Column(nullable = false)
    val placeId: Long,
) : Serializable

/**
 * 칩에 속한 매장. 조건 매칭이 아니라 운영이 고른 목록이다.
 *
 * 읽기 경로(검색·핀)는 네이티브 쿼리로 이 테이블을 직접 걸고, 쓰기는 어드민
 * 교체(`CurationTagPlaceRepository`, TMT-421)가 이 엔티티로 넣고 지운다.
 * 엔티티가 매핑돼 있으면 `ddl-auto: validate`가 스키마 어긋남을 기동에서 잡는다 —
 * 네이티브만 있으면 컬럼 오타를 그 화면을 열어야 안다.
 *
 * [pinOrder]를 **읽는** 경로는 운영 조회뿐이다 — 검색·핀은 기존 정렬(거리순·유사도순)을 쓴다.
 * 칩이 필터인지 결과 형태를 바꾸는지가 도메인 v2 §7-1의 미결(E5)이라 정렬 축을
 * 새로 만들지 않았다.
 */
@Entity
@Table(name = "curation_tag_place")
class CurationTagPlaceEntity(
    @EmbeddedId
    val id: CurationTagPlaceId,
    @Column(nullable = false)
    var pinOrder: Short,
) : BaseCreatedEntity()
