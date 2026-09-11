package com.tmt.application.port.output.persistence

/**
 * 큐레이션 칩 쓰기·운영 조회 (TMT-421). 공개 읽기([CurationTagPort])와 포트를 가른 이유는
 * 검색·핀 서비스가 쓰기 메서드를 볼 이유가 없기 때문이다 — place의 query/command 분리와 같다.
 */
interface CurationTagAdminPort {
    /** display_order 순, **비활성 포함** */
    fun findAllTags(): List<CurationTagAdminRow>

    fun findTag(curationTagId: String): CurationTagAdminRow?

    /** 이미 있으면 false — 호출부가 CURATION_TAG_DUPLICATED로 끊는다 */
    fun createTag(
        curationTagId: String,
        label: String,
        displayOrder: Int,
    ): Boolean

    /** null인 인자는 건드리지 않는다. 없는 칩이면 false */
    fun updateTag(
        curationTagId: String,
        label: String?,
        displayOrder: Int?,
        active: Boolean?,
    ): Boolean

    /** pin_order 순 */
    fun findTagPlaces(curationTagId: String): List<CurationTagPlaceRow>

    /**
     * 칩의 매장 집합을 통째로 교체한다. [placeIds]의 순서가 `pin_order` 1..n이 된다.
     * 한 트랜잭션에서 지우고 넣는다 — 중간 상태가 검색에 보이면 안 된다.
     */
    fun replaceTagPlaces(
        curationTagId: String,
        placeIds: List<Long>,
    )

    /** [placeIds] 중 실제로 없는 id들 — 하나라도 있으면 호출부가 교체를 거부한다 */
    fun findMissingPlaceIds(placeIds: List<Long>): List<Long>
}

data class CurationTagAdminRow(
    val curationTagId: String,
    val label: String,
    val displayOrder: Int,
    val active: Boolean,
    val placeCount: Int,
)

data class CurationTagPlaceRow(
    val placeId: Long,
    val placeName: String,
    val roadAddress: String,
    val pinOrder: Int,
)
