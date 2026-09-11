package com.tmt.output.persistence.postgres.repository

import com.tmt.output.persistence.postgres.entity.CurationTagEntity
import com.tmt.output.persistence.postgres.entity.CurationTagPlaceEntity
import com.tmt.output.persistence.postgres.entity.CurationTagPlaceId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

/**
 * 큐레이션 칩 운영 쿼리 (TMT-421).
 *
 * 쓰기를 네이티브로 두는 이유는 셋이다 — 생성은 `ON CONFLICT DO NOTHING`으로 **경쟁 중
 * 덮어쓰기를 막고**, 부분 수정은 `COALESCE`로 **null인 필드를 건드리지 않고**, 둘 다
 * 영향 행 수로 "있었나/없었나"를 그대로 돌려준다. 네이티브는 기동 시 검증되지 않으므로
 * `CurationTagAdminAdapterTest`가 실제 PostGIS에 태운다.
 */
interface CurationTagAdminRepository : JpaRepository<CurationTagEntity, String> {
    /** 비활성 포함, display_order 순. 매장 수를 함께 센다 — 0곳인 칩을 운영이 알아야 한다 */
    @Query(
        value = """
            SELECT c.id            AS curationTagId,
                   c.label         AS label,
                   c.display_order AS displayOrder,
                   c.active        AS active,
                   (
                       SELECT count(*) FROM curation_tag_place cp
                       WHERE cp.curation_tag_id = c.id
                   )               AS placeCount
            FROM curation_tag c
            WHERE CAST(:curationTagId AS varchar) IS NULL OR c.id = :curationTagId
            ORDER BY c.display_order, c.id
        """,
        nativeQuery = true,
    )
    fun findTagRows(
        @Param("curationTagId") curationTagId: String?,
    ): List<CurationTagAdminRowView>

    interface CurationTagAdminRowView {
        fun getCurationTagId(): String

        fun getLabel(): String

        fun getDisplayOrder(): Int

        fun getActive(): Boolean

        fun getPlaceCount(): Int
    }

    /** 이미 있으면 0을 돌려준다 — 조회 후 INSERT와 달리 경쟁에서도 덮어쓰지 않는다 */
    @Modifying
    @Query(
        value = """
            INSERT INTO curation_tag (id, label, display_order)
            VALUES (:curationTagId, :label, CAST(:displayOrder AS smallint))
            ON CONFLICT (id) DO NOTHING
        """,
        nativeQuery = true,
    )
    fun insertTagIfAbsent(
        @Param("curationTagId") curationTagId: String,
        @Param("label") label: String,
        @Param("displayOrder") displayOrder: Int,
    ): Int

    /**
     * null인 인자는 기존 값을 유지한다. `updated_at`을 직접 찍는 이유 — 네이티브 UPDATE는
     * JPA 감사(`@LastModifiedDate`)를 거치지 않아 가만히 두면 시각이 생성 시점에 멈춘다.
     */
    @Modifying
    @Query(
        value = """
            UPDATE curation_tag
            SET label         = COALESCE(CAST(:label AS varchar), label),
                display_order = COALESCE(CAST(:displayOrder AS smallint), display_order),
                active        = COALESCE(CAST(:active AS boolean), active),
                updated_at    = now()
            WHERE id = :curationTagId
        """,
        nativeQuery = true,
    )
    fun updateTag(
        @Param("curationTagId") curationTagId: String,
        @Param("label") label: String?,
        @Param("displayOrder") displayOrder: Int?,
        @Param("active") active: Boolean?,
    ): Int

    /** pin_order 순. 이름·주소는 운영 화면이 매장을 식별하려면 필요하다 */
    @Query(
        value = """
            SELECT p.id           AS placeId,
                   p.name         AS placeName,
                   p.road_address AS roadAddress,
                   cp.pin_order   AS pinOrder
            FROM curation_tag_place cp
            JOIN place p ON p.id = cp.place_id
            WHERE cp.curation_tag_id = :curationTagId
            ORDER BY cp.pin_order, p.id
        """,
        nativeQuery = true,
    )
    fun findTagPlaceRows(
        @Param("curationTagId") curationTagId: String,
    ): List<CurationTagPlaceRowView>

    interface CurationTagPlaceRowView {
        fun getPlaceId(): Long

        fun getPlaceName(): String

        fun getRoadAddress(): String

        fun getPinOrder(): Int
    }

    /** 주어진 id 중 실제로 있는 것들 — 호출부가 차집합으로 없는 id를 찾는다 */
    @Query(
        value = "SELECT p.id FROM place p WHERE p.id IN :placeIds",
        nativeQuery = true,
    )
    fun findExistingPlaceIds(
        @Param("placeIds") placeIds: Collection<Long>,
    ): List<Long>
}

/** 칩의 매장 집합. 전체 교체라 삭제 후 삽입이고, 둘이 한 트랜잭션에 있어야 한다 */
interface CurationTagPlaceRepository : JpaRepository<CurationTagPlaceEntity, CurationTagPlaceId> {
    /**
     * **벌크 삭제여야 한다.** 파생 삭제(`deleteByIdCurationTagId`)는 영속성 컨텍스트에
     * 삭제를 쌓아두는데, Hibernate는 flush에서 삽입을 삭제보다 **먼저** 내보낸다 —
     * 같은 칩에 같은 매장을 다시 넣는 교체에서 PK 충돌이 난다. JPQL 벌크 삭제는
     * 호출 시점에 SQL로 나가므로 뒤따르는 삽입이 안전하다.
     */
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM CurationTagPlaceEntity cp WHERE cp.id.curationTagId = :curationTagId")
    fun deleteAllByCurationTagId(
        @Param("curationTagId") curationTagId: String,
    ): Int
}
