package com.tmt.output.persistence.postgres.repository

import com.tmt.output.persistence.postgres.entity.GroupEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface GroupStatsRepository : JpaRepository<GroupEntity, Long> {
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE GroupEntity g SET g.memberCount = g.memberCount + 1 WHERE g.id = :groupId")
    fun addMember(
        @Param("groupId") groupId: Long,
    ): Int

    /** 생성자는 탈퇴할 수 없어(G11) 1 밑으로는 내려가지 않는다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE GroupEntity g SET g.memberCount = g.memberCount - 1 WHERE g.id = :groupId AND g.memberCount > 1")
    fun removeMember(
        @Param("groupId") groupId: Long,
    ): Int

    /**
     * group_place를 공유 집합에서 통째로 다시 만든다. 증감으로 맞추면 같은 매장에 공유가
     * 겹칠 때(D3) 행 수와 place_count가 어긋난다. 삭제된 리뷰는 세지 않는다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM GroupPlaceEntity p WHERE p.id.groupId = :groupId")
    fun clearGroupPlaces(
        @Param("groupId") groupId: Long,
    ): Int

    /** JPQL에 INSERT가 없어 네이티브다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        value = """
            INSERT INTO group_place (group_id, place_id, shared_review_count)
            SELECT s.group_id, r.place_id, count(*)
            FROM group_review_share s
                     JOIN review r ON r.id = s.review_id AND r.deleted_at IS NULL
            WHERE s.group_id = :groupId
            GROUP BY s.group_id, r.place_id
        """,
        nativeQuery = true,
    )
    fun rebuildGroupPlaces(
        @Param("groupId") groupId: Long,
    ): Int

    /** place_count는 group_place 행 수와 같다 — 위에서 다시 만든 뒤에 호출한다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        """
        UPDATE GroupEntity g
        SET g.reviewCount = cast(
                (
                    SELECT count(s)
                    FROM GroupReviewShareEntity s, ReviewEntity r
                    WHERE r.id = s.reviewId AND r.deletedAt IS NULL AND s.groupId = g.id
                ) as Integer
            ),
            g.placeCount = cast(
                (SELECT count(p) FROM GroupPlaceEntity p WHERE p.id.groupId = g.id) as Integer
            ),
            g.updatedAt = :now
        WHERE g.id = :groupId
        """,
    )
    fun refreshShareCounts(
        @Param("groupId") groupId: Long,
        @Param("now") now: Instant,
    ): Int
}
