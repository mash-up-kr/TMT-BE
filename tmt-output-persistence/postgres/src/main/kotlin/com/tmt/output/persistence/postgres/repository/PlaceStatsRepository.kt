package com.tmt.output.persistence.postgres.repository

import com.tmt.output.persistence.postgres.entity.PlaceEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface PlaceStatsRepository : JpaRepository<PlaceEntity, Long> {
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        """
        UPDATE PlaceEntity p
        SET p.reviewCount = p.reviewCount + 1, p.ratingSum = p.ratingSum + :rating
        WHERE p.id = :placeId
        """,
    )
    fun addReview(
        @Param("placeId") placeId: Long,
        @Param("rating") rating: Int,
    ): Int

    /**
     * 음수로 내려가지 않게 WHERE에서 막는다 — 집계가 어긋난 상태에서 차감이 반복되면
     * 평균 별점(P9)과 지도 핀 조건(E6)이 같이 깨진다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        """
        UPDATE PlaceEntity p
        SET p.reviewCount = p.reviewCount - 1, p.ratingSum = p.ratingSum - :rating
        WHERE p.id = :placeId AND p.reviewCount > 0 AND p.ratingSum >= :rating
        """,
    )
    fun removeReview(
        @Param("placeId") placeId: Long,
        @Param("rating") rating: Int,
    ): Int
}
