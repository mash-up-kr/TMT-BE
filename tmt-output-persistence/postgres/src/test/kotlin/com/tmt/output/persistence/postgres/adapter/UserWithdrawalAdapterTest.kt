package com.tmt.output.persistence.postgres.adapter

import com.tmt.application.domain.user.UserWithdrawalService
import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException
import com.tmt.output.persistence.postgres.support.PersistenceTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * 회원탈퇴 (TMT-409). 삭제 순서와 집계 차감을 **실제 FK가 걸린 DB에서** 본다 —
 * 순서가 틀리면 그 자리에서 FK 위반으로 멈추고, 조건 하나가 빠지면 지표만 조용히 어긋난다.
 *
 * 어댑터가 아니라 서비스까지 함께 띄운다. 삭제는 어댑터가 하고 집계 차감은 서비스가 하므로
 * 둘을 나눠 보면 "지워졌지만 집계가 안 맞는" 실패가 어느 쪽 테스트에도 안 잡힌다.
 */
@Import(
    UserWithdrawalService::class,
    UserWithdrawalAdapter::class,
    UserAccountAdapter::class,
    PlaceStatsAdapter::class,
    GroupStatsAdapter::class,
)
class UserWithdrawalAdapterTest : PersistenceTest() {
    @Autowired
    private lateinit var service: UserWithdrawalService

    @Test
    fun `탈퇴하면 이 사용자의 행이 FK 순서대로 전부 사라진다`() {
        val user = fixtures.newUser()
        val place = fixtures.newPlace()
        val review = fixtures.newPublishedReview(place, user)
        fixtures.attachPhoto(review.saveId, fixtures.newMediaAsset(user), photoOrder = 0)
        fixtures.addTag(review.saveId, "tag_alone")
        fixtures.addSummary(review.reviewId)
        fixtures.attachProfileImage(user)
        fixtures.addFavorite(user, place)
        fixtures.newTicket(user)
        val hostGroup = fixtures.newGroup(fixtures.newUser())
        fixtures.newMembership(hostGroup, user)
        fixtures.shareReview(hostGroup, review.reviewId, user)
        jdbcTemplate.update(
            """
            INSERT INTO idempotency_key
                (user_id, endpoint, idem_key, request_fingerprint, response_status, response_body)
            VALUES (?, 'POST /v1/saves', 'k1', 'f1', 201, '{}'::jsonb)
            """.trimIndent(),
            user,
        )

        service.withdraw(user)

        assertEquals(0, countOf("users", "id", user))
        listOf(
            "save" to "user_id",
            "review" to "user_id",
            "place_favorite" to "user_id",
            "reward_grant" to "user_id",
            "group_join_ticket" to "user_id",
            "group_membership" to "user_id",
            "group_review_share" to "user_id",
            "idempotency_key" to "user_id",
            "media_asset" to "owner_id",
        ).forEach { (table, column) ->
            assertEquals(0, countOf(table, column, user), "$table 이 남았다")
        }
        assertEquals(0, count("SELECT count(*) FROM save_photo WHERE save_id = ${review.saveId}"))
        assertEquals(0, count("SELECT count(*) FROM save_tag WHERE save_id = ${review.saveId}"))
        assertEquals(0, count("SELECT count(*) FROM review_ai_summary WHERE review_id = ${review.reviewId}"))
    }

    @Test
    fun `없는 사용자는 USER_NOT_FOUND다`() {
        val error = assertFailsWith<TmtException> { service.withdraw(-1L) }

        assertEquals(ErrorCode.USER_NOT_FOUND, error.errorCode)
    }

    @Test
    fun `소유 그룹은 멤버가 남아 있어도 그룹째 사라지고 남의 리뷰는 남는다`() {
        // G13에서 owner_id가 불변이라 넘길 자리가 없다 — 그룹을 지우는 대신 남의 리뷰까지 지우지는 않는다
        val owner = fixtures.newUser()
        val other = fixtures.newUser()
        val group = fixtures.newGroup(owner)
        fixtures.newMembership(group, owner)
        fixtures.newMembership(group, other)
        fixtures.addRegionTag(group, "region_seoul_all")
        val place = fixtures.newPlace()
        val theirReview = fixtures.newPublishedReview(place, other)
        fixtures.shareReview(group, theirReview.reviewId, other)
        fixtures.addGroupPlace(group, place)

        service.withdraw(owner)

        assertEquals(0, count("SELECT count(*) FROM groups WHERE id = $group"))
        listOf("group_membership", "group_review_share", "group_place", "group_region_tag").forEach {
            assertEquals(0, count("SELECT count(*) FROM $it WHERE group_id = $group"), "$it 이 남았다")
        }
        assertEquals(1, count("SELECT count(*) FROM review WHERE id = ${theirReview.reviewId}"))
        assertEquals(1, count("SELECT count(*) FROM users WHERE id = $other"))
    }

    @Test
    fun `매장 집계에서 내 리뷰 몫만 차감한다`() {
        val user = fixtures.newUser()
        val other = fixtures.newUser()
        // 남의 리뷰 1건(별점 3) + 내 리뷰 1건(별점 5)이 이미 반영된 상태
        val place = fixtures.newPlace(reviewCount = 2, ratingSum = 8)
        fixtures.newPublishedReview(place, other, rating = 3)
        fixtures.newPublishedReview(place, user, rating = 5)

        service.withdraw(user)

        assertEquals(1, count("SELECT review_count FROM place WHERE id = $place"))
        assertEquals(3, count("SELECT rating_sum FROM place WHERE id = $place"))
    }

    @Test
    fun `이미 삭제된 리뷰는 두 번 차감하지 않는다`() {
        // deleted_at 조건이 빠지면 리뷰 삭제 때 한 번, 탈퇴 때 또 한 번 빠져 평균이 영구히 틀어진다
        val user = fixtures.newUser()
        val place = fixtures.newPlace(reviewCount = 1, ratingSum = 5)
        fixtures.newPublishedReview(place, user, rating = 5)
        fixtures.newPublishedReview(place, user, rating = 4, deletedAt = Instant.now())

        service.withdraw(user)

        assertEquals(0, count("SELECT review_count FROM place WHERE id = $place"))
        assertEquals(0, count("SELECT rating_sum FROM place WHERE id = $place"))
    }

    @Test
    fun `남의 그룹은 가입자 수를 차감하고 공유 집계를 다시 센다`() {
        val owner = fixtures.newUser()
        val user = fixtures.newUser()
        val group = fixtures.newGroup(owner)
        fixtures.newMembership(group, owner)
        fixtures.newMembership(group, user)
        val place = fixtures.newPlace()
        val ownerReview = fixtures.newPublishedReview(place, owner)
        val myReview = fixtures.newPublishedReview(fixtures.newPlace(), user)
        fixtures.shareReview(group, ownerReview.reviewId, owner)
        fixtures.shareReview(group, myReview.reviewId, user)
        jdbcTemplate.update("UPDATE groups SET member_count = 2, review_count = 2, place_count = 2 WHERE id = $group")

        service.withdraw(user)

        assertEquals(1, count("SELECT member_count FROM groups WHERE id = $group"))
        assertEquals(1, count("SELECT review_count FROM groups WHERE id = $group"))
        assertEquals(1, count("SELECT place_count FROM groups WHERE id = $group"))
        assertEquals(1, count("SELECT count(*) FROM group_place WHERE group_id = $group"))
    }

    @Test
    fun `남이 내 리뷰를 공유해 둔 그룹도 집계가 맞는다`() {
        // 내가 공유한 것만 보면 이 그룹이 대상에서 빠져 리뷰가 사라진 뒤에도 review_count가 남는다
        val owner = fixtures.newUser()
        val user = fixtures.newUser()
        val group = fixtures.newGroup(owner)
        fixtures.newMembership(group, owner)
        val place = fixtures.newPlace()
        val myReview = fixtures.newPublishedReview(place, user)
        fixtures.shareReview(group, myReview.reviewId, owner)
        jdbcTemplate.update("UPDATE groups SET member_count = 1, review_count = 1, place_count = 1 WHERE id = $group")

        service.withdraw(user)

        assertEquals(1, count("SELECT member_count FROM groups WHERE id = $group"), "가입한 적 없으니 그대로다")
        assertEquals(0, count("SELECT review_count FROM groups WHERE id = $group"))
        assertEquals(0, count("SELECT place_count FROM groups WHERE id = $group"))
    }

    @Test
    fun `같은 카카오 계정으로 다시 가입할 수 있다`() {
        // kakao_id가 UNIQUE라 행이 남아 있으면 재가입이 막힌다 (U1)
        val user = fixtures.newUser()
        val kakaoId = jdbcTemplate.queryForObject("SELECT kakao_id FROM users WHERE id = $user", Long::class.java)!!

        service.withdraw(user)

        val inserted =
            jdbcTemplate.update("INSERT INTO users (kakao_id, nickname) VALUES (?, '다시가입')", kakaoId)
        assertTrue(inserted == 1)
    }

    private fun countOf(
        table: String,
        column: String,
        userId: Long,
    ) = count("SELECT count(*) FROM $table WHERE $column = $userId")

    private fun count(sql: String) = jdbcTemplate.queryForObject(sql, Int::class.java)!!
}
