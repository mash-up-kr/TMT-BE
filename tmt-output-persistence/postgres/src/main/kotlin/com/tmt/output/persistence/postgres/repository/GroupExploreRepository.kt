package com.tmt.output.persistence.postgres.repository

import com.tmt.output.persistence.postgres.entity.GroupEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

/** 그룹 탐색 목록 (D_01 §2, TMT-220). */
interface GroupExploreRepository : JpaRepository<GroupEntity, Long> {
    /**
     * 정렬 3종을 (k1, k2, id) 한 형태로 통일한다 — RECOMMENDED는 (일치 저장 수, 멤버 수),
     * MEMBER_COUNT·REVIEW_COUNT는 (정렬 값, 0). 키셋 비교가 하나로 끝나고,
     * 같은 정렬 값이 페이지 경계에 걸려도 id가 tie-breaker라 중복·누락이 없다 (TMT-178).
     *
     * CASE 정렬이라 groups_recommend_ix를 못 타지만 그룹은 수백 규모라 감수한다 (G17).
     * 일치 저장 수(G12)는 group_place(파생 집계, D3)와 내 저장의 교집합이다.
     */
    @Query(
        value = """
            WITH my_place AS (
                SELECT DISTINCT sv.place_id
                FROM save sv
                WHERE sv.user_id = CAST(:viewerId AS bigint) AND sv.deleted_at IS NULL
            ),
            card AS (
                SELECT g.id            AS groupId,
                       g.name          AS name,
                       g.one_line_description AS oneLineDescription,
                       g.member_count  AS memberCount,
                       g.review_count  AS reviewCount,
                       g.place_count   AS placeCount,
                       COALESCE(m.matched, 0) AS matchedSavedPlaceCount,
                       CASE CAST(:sort AS text)
                           WHEN 'RECOMMENDED'  THEN COALESCE(m.matched, 0)
                           WHEN 'MEMBER_COUNT' THEN g.member_count
                           ELSE g.review_count
                       END AS sortKey1,
                       CASE CAST(:sort AS text) WHEN 'RECOMMENDED' THEN g.member_count ELSE 0 END AS sortKey2,
                       cover.s3_key AS coverS3Key
                FROM groups g
                LEFT JOIN LATERAL (
                    SELECT count(*) AS matched
                    FROM group_place gp
                    WHERE gp.group_id = g.id AND gp.place_id IN (SELECT place_id FROM my_place)
                ) m ON CAST(:viewerId AS bigint) IS NOT NULL
                LEFT JOIN LATERAL (
                    -- 커버 정본은 GroupCoverSql이다 (G16). 상세(N장)와 같은 자구를 쓴다
                    SELECT ma.s3_key
                    ${GroupCoverSql.FROM_JOINS}
                    WHERE s.group_id = g.id
                    ${GroupCoverSql.ORDER_BY}
                    LIMIT 1
                ) cover ON true
                WHERE (CAST(:foodCategoryId AS text) IS NULL OR g.food_category_id = :foodCategoryId)
                  -- 홈 추천 캐러셀은 이미 가입한 그룹을 뺀다 (A §5-3). 탐색 목록은 null로 넘겨 전체를 본다
                  AND (CAST(:excludeJoinedBy AS bigint) IS NULL OR NOT EXISTS (
                          SELECT 1 FROM group_membership gm
                          WHERE gm.group_id = g.id
                            AND gm.user_id = CAST(:excludeJoinedBy AS bigint)
                            AND gm.status = 'ACTIVE'
                      ))
                  AND (CAST(:regionCsv AS text) IS NULL OR EXISTS (
                          SELECT 1 FROM group_region_tag t
                          WHERE t.group_id = g.id AND t.region_tag_id = ANY(string_to_array(:regionCsv, ','))
                      ))
                  AND (CAST(:queryPattern AS text) IS NULL
                       OR g.name ILIKE :queryPattern ESCAPE '\'
                       OR g.one_line_description ILIKE :queryPattern ESCAPE '\'
                       OR (CAST(:queryFoodCsv AS text) IS NOT NULL
                           AND g.food_category_id = ANY(string_to_array(:queryFoodCsv, ',')))
                       OR (CAST(:queryRegionCsv AS text) IS NOT NULL AND EXISTS (
                               SELECT 1 FROM group_region_tag t
                               WHERE t.group_id = g.id AND t.region_tag_id = ANY(string_to_array(:queryRegionCsv, ','))
                           )))
            )
            SELECT * FROM card c
            WHERE CAST(:afterK1 AS bigint) IS NULL
               OR (c.sortKey1, c.sortKey2, c.groupId) < (CAST(:afterK1 AS bigint), CAST(:afterK2 AS bigint), CAST(:afterGroupId AS bigint))
            ORDER BY c.sortKey1 DESC, c.sortKey2 DESC, c.groupId DESC
            LIMIT :limitPlusOne
        """,
        nativeQuery = true,
    )
    fun findGroupCards(
        @Param("viewerId") viewerId: Long?,
        /** LikePatterns.contains로 이스케이프된 부분 일치 패턴. null이면 검색 없음. */
        @Param("queryPattern") queryPattern: String?,
        @Param("queryFoodCsv") queryFoodCsv: String?,
        @Param("queryRegionCsv") queryRegionCsv: String?,
        @Param("foodCategoryId") foodCategoryId: String?,
        @Param("regionCsv") regionCsv: String?,
        /** 이 사용자가 ACTIVE로 가입한 그룹을 후보에서 뺀다. null이면 빼지 않는다 (A §5-3). */
        @Param("excludeJoinedBy") excludeJoinedBy: Long?,
        @Param("sort") sort: String,
        @Param("afterK1") afterK1: Long?,
        @Param("afterK2") afterK2: Long?,
        @Param("afterGroupId") afterGroupId: Long?,
        @Param("limitPlusOne") limitPlusOne: Int,
    ): List<GroupCardRowView>

    fun existsByName(name: String): Boolean

    interface GroupCardRowView {
        fun getGroupId(): Long

        fun getName(): String

        fun getOneLineDescription(): String

        fun getCoverS3Key(): String?

        fun getMemberCount(): Int

        fun getReviewCount(): Int

        fun getPlaceCount(): Int

        fun getMatchedSavedPlaceCount(): Long

        fun getSortKey1(): Long

        fun getSortKey2(): Long
    }
}
