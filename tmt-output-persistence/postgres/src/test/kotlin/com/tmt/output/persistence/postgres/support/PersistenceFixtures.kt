package com.tmt.output.persistence.postgres.support

import org.springframework.jdbc.core.JdbcTemplate
import java.sql.Timestamp
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong

/**
 * 통합 테스트가 쓸 행을 직접 INSERT 한다.
 *
 * 컨테이너를 모듈 전체가 공유하고 롤백도 꺼져 있어(TMT-295), **앞선 실행의 데이터가 남은 DB에서도
 * 결과가 같아야 한다.** 그래서 두 가지를 지킨다.
 *
 * - 여기서 만드는 행은 전부 새 행이다. UNIQUE 컬럼(`kakao_id`·`external_id`·`s3_key`)에는
 *   [nextSequence]로 만든 값이 들어가 이전 실행분과 겹치지 않는다
 * - 전역 집계에 단언하지 않는다. 테스트는 자기가 만든 id로만 결과를 좁힌다
 *
 * "이 행이 없어야 한다"가 전제인 테스트는 [deleteFavorite]처럼 그 행만 좁게 지우고 시작한다 —
 * 테이블을 통째로 비우면 같은 컨테이너를 쓰는 다른 테스트가 깨진다.
 */
class PersistenceFixtures(
    private val jdbcTemplate: JdbcTemplate,
) {
    fun newUser(nickname: String = "tester"): Long =
        insertReturningId(
            "INSERT INTO users (kakao_id, nickname) VALUES (?, ?) RETURNING id",
            nextSequence(),
            nickname,
        )

    /**
     * 좌표는 `ST_MakePoint(경도, 위도)` 순서다 — 뒤집으면 거리가 통째로 달라지는데도 쿼리는 통과한다.
     * [reviewCount]는 지도 핀 조건(E6 `review_count > 0`)이 보는 값이라 리뷰를 만들어도 자동으로
     * 오르지 않는다 — 핀을 기대하는 테스트가 직접 준다.
     */
    fun newPlace(
        name: String = "테스트매장",
        latitude: Double = SEOUL_CITY_HALL_LAT,
        longitude: Double = SEOUL_CITY_HALL_LNG,
        roadAddress: String = "서울특별시 중구 세종대로 110",
        regionName: String = "중구 태평로1가",
        categoryId: String? = "cat_korean",
        reviewCount: Int = 0,
        ratingSum: Long = 0,
    ): Long =
        insertReturningId(
            """
            INSERT INTO place (
                external_source, external_id, name, road_address, jibun_address,
                region_name, category_id, location, review_count, rating_sum
            ) VALUES (
                'TEST', ?, ?, ?, NULL,
                ?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, ?, ?
            )
            RETURNING id
            """.trimIndent(),
            nextSequence().toString(),
            name,
            roadAddress,
            regionName,
            categoryId,
            longitude,
            latitude,
            reviewCount,
            ratingSum,
        )

    fun newSave(
        userId: Long,
        placeId: Long,
        rating: Int? = 5,
        content: String? = "맛있었다",
    ): Long =
        insertReturningId(
            "INSERT INTO save (user_id, place_id, rating, content) VALUES (?, ?, ?, ?) RETURNING id",
            userId,
            placeId,
            rating,
            content,
        )

    /** [createdAt]은 `(created_at, id)` 커서를 보는 테스트가 순서를 직접 정하려고 넘긴다. */
    fun newReview(
        saveId: Long,
        userId: Long,
        placeId: Long,
        createdAt: Instant = Instant.now(),
        deletedAt: Instant? = null,
    ): Long =
        insertReturningId(
            """
            INSERT INTO review (save_id, user_id, place_id, created_at, deleted_at)
            VALUES (?, ?, ?, ?, ?)
            RETURNING id
            """.trimIndent(),
            saveId,
            userId,
            placeId,
            java.sql.Timestamp.from(createdAt),
            deletedAt?.let { java.sql.Timestamp.from(it) },
        )

    /** user·save·review를 한 번에 만든다 — 리뷰 목록 테스트가 매번 세 줄을 반복하지 않게. */
    fun newPublishedReview(
        placeId: Long,
        userId: Long = newUser(),
        rating: Int = 5,
        content: String = "맛있었다",
        createdAt: Instant = Instant.now(),
        deletedAt: Instant? = null,
    ): PublishedReview {
        val saveId = newSave(userId, placeId, rating, content)
        val reviewId = newReview(saveId, userId, placeId, createdAt, deletedAt)
        return PublishedReview(reviewId = reviewId, saveId = saveId, userId = userId)
    }

    fun newMediaAsset(
        ownerId: Long,
        status: String = "STAGED",
        createdAt: Instant? = null,
    ): Long =
        insertReturningId(
            """
            INSERT INTO media_asset (owner_id, s3_key, content_type, content_length, status, created_at)
            VALUES (?, ?, 'image/jpeg', 1024, ?, COALESCE(?, now()))
            RETURNING id
            """.trimIndent(),
            ownerId,
            "test/${nextSequence()}.jpg",
            status,
            createdAt?.let { java.sql.Timestamp.from(it) },
        )

    /** save_photo는 `(save_id, photo_order)`가 UNIQUE라 같은 save 안에서 order가 겹치면 안 된다. */
    fun attachPhoto(
        saveId: Long,
        mediaAssetId: Long,
        photoOrder: Int = 0,
    ): Long =
        insertReturningId(
            "INSERT INTO save_photo (save_id, media_asset_id, photo_order) VALUES (?, ?, ?) RETURNING id",
            saveId,
            mediaAssetId,
            photoOrder,
        )

    fun addTag(
        saveId: Long,
        tagId: String,
    ) {
        jdbcTemplate.update("INSERT INTO save_tag (save_id, tag_id) VALUES (?, ?)", saveId, tagId)
    }

    fun addSummary(
        reviewId: Long,
        pros: String? = "친절함",
        cons: String? = "웨이팅",
    ) {
        jdbcTemplate.update(
            "INSERT INTO review_ai_summary (review_id, pros, cons, model) VALUES (?, ?, ?, 'test')",
            reviewId,
            pros,
            cons,
        )
    }

    fun addFavorite(
        userId: Long,
        placeId: Long,
    ) {
        jdbcTemplate.update("INSERT INTO place_favorite (user_id, place_id) VALUES (?, ?)", userId, placeId)
    }

    /** 찜이 없는 상태에서 시작해야 하는 테스트용 — 그 한 행만 지운다. */
    fun deleteFavorite(
        userId: Long,
        placeId: Long,
    ) {
        jdbcTemplate.update("DELETE FROM place_favorite WHERE user_id = ? AND place_id = ?", userId, placeId)
    }

    /** 그룹. `name`이 UNIQUE(G6)라 유일값을 넣는다. `member_count`는 생성자 포함 1에서 시작한다 */
    fun newGroup(
        ownerId: Long,
        foodCategoryId: String = "cat_korean",
    ): Long =
        insertReturningId(
            """
            INSERT INTO groups (name, one_line_description, food_category_id, owner_id)
            VALUES (?, '한 줄 소개', ?, ?)
            RETURNING id
            """.trimIndent(),
            "테스트그룹${nextSequence()}",
            foodCategoryId,
            ownerId,
        )

    /**
     * 발급 근거 1건 (T8). `(source_type, source_id, reward_type)`이 UNIQUE라 source_id에 유일값을 넣는다 —
     * 실제 리뷰가 아니어도 FK가 없어 성립한다. [newTicket]이 쓰고, 회수 테스트는 리뷰 id를 직접 준다.
     */
    fun grantReward(
        userId: Long,
        sourceType: String = "REVIEW",
        sourceId: Long = nextSequence(),
    ): Long =
        insertReturningId(
            """
            INSERT INTO reward_grant (user_id, reward_type, source_type, source_id)
            VALUES (?, 'GROUP_JOIN_TICKET', ?, ?)
            RETURNING id
            """.trimIndent(),
            userId,
            sourceType,
            sourceId,
        )

    /**
     * 티켓 1장. 기본은 `AVAILABLE`이고, `CONSUMED`·`REVOKED`로 이미 쓰였거나 회수된 장을 만들 수 있다 —
     * 티켓 이력(T10)처럼 상태별 행이 필요한 테스트가 쓴다.
     */
    fun newTicket(
        userId: Long,
        status: String = "AVAILABLE",
        rewardGrantId: Long = grantReward(userId),
        consumedGroupId: Long? = null,
        consumedAt: Instant? = null,
        revokedAt: Instant? = null,
    ): Long =
        insertReturningId(
            """
            INSERT INTO group_join_ticket (user_id, reward_grant_id, status, consumed_group_id, consumed_at, revoked_at)
            VALUES (?, ?, ?, ?, ?, ?)
            RETURNING id
            """.trimIndent(),
            userId,
            rewardGrantId,
            status,
            consumedGroupId,
            consumedAt?.let(Timestamp::from),
            revokedAt?.let(Timestamp::from),
        )

    /** 공유는 `(group_id, review_id)`가 UNIQUE라 같은 리뷰를 한 그룹에 두 번 넣을 수 없다 (share_uq) */
    fun shareReview(
        groupId: Long,
        reviewId: Long,
        userId: Long,
    ): Long =
        insertReturningId(
            "INSERT INTO group_review_share (group_id, review_id, user_id) VALUES (?, ?, ?) RETURNING id",
            groupId,
            reviewId,
            userId,
        )

    /**
     * 가입. [joinedAt]은 가입 오래된 순(G20) 커서를 보는 테스트가 넘긴다.
     * `(group_id, user_id)`는 ACTIVE일 때만 UNIQUE라(D5) 같은 쌍을 LEFT로는 여러 번 넣을 수 있다.
     */
    fun newMembership(
        groupId: Long,
        userId: Long,
        joinedAt: Instant = Instant.now(),
        status: String = "ACTIVE",
    ): Long =
        insertReturningId(
            """
            INSERT INTO group_membership (group_id, user_id, status, joined_at)
            VALUES (?, ?, ?, ?)
            RETURNING id
            """.trimIndent(),
            groupId,
            userId,
            status,
            java.sql.Timestamp.from(joinedAt),
        )

    private fun insertReturningId(
        sql: String,
        vararg args: Any?,
    ): Long = jdbcTemplate.queryForObject(sql, { rs, _ -> rs.getLong("id") }, *args)

    data class PublishedReview(
        val reviewId: Long,
        val saveId: Long,
        val userId: Long,
    )

    companion object {
        const val SEOUL_CITY_HALL_LAT = 37.5666
        const val SEOUL_CITY_HALL_LNG = 126.9784

        private val sequence = AtomicLong(System.currentTimeMillis() * 1_000)

        /**
         * UNIQUE 컬럼에 넣을 값. 밀리초 기점에서 올라가므로 같은 컨테이너를 재사용하는
         * 다음 실행과도 겹치지 않는다.
         */
        fun nextSequence(): Long = sequence.incrementAndGet()
    }
}
