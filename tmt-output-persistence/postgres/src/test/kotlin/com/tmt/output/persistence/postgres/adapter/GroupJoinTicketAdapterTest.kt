package com.tmt.output.persistence.postgres.adapter

import com.tmt.output.persistence.postgres.support.PersistenceTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 티켓 회수 (R7·TX-5) — TMT-348 전수 커버.
 *
 * 소비(`consumeOne`)는 동시성 테스트가 덮고 있는데 회수만 비어 있었다. 회수는
 * **조건부 UPDATE 한 문장**이라 "회수할 AVAILABLE이 없다"는 0행 판정이 SQL에서만 드러난다.
 *
 * 이 어댑터는 `@Transactional`을 달지 않는다 — 호출부(리뷰 삭제 TX-5)가 연 트랜잭션에
 * 참여하는 설계라서다. 그래서 쓰기를 [inTransaction]으로 감싼다.
 */
@Import(GroupJoinTicketAdapter::class)
class GroupJoinTicketAdapterTest : PersistenceTest() {
    @Autowired
    private lateinit var adapter: GroupJoinTicketAdapter

    @Test
    fun `리뷰로 받은 티켓을 회수한다`() {
        val user = fixtures.newUser()
        val review = fixtures.newPublishedReview(fixtures.newPlace(), user)
        inTransaction { adapter.grantForReview(user, review.reviewId) }
        assertEquals(1, adapter.countAvailable(user))

        assertTrue(inTransaction { adapter.revokeOneForReview(user, review.reviewId) })
        assertEquals(0, adapter.countAvailable(user))
    }

    @Test
    fun `회수할 티켓이 없으면 false다`() {
        // 이미 그룹 가입에 쓴 티켓은 되돌릴 수 없다 — 잔고 0에서 회수는 실패다
        val user = fixtures.newUser()
        val review = fixtures.newPublishedReview(fixtures.newPlace(), user)

        assertFalse(inTransaction { adapter.revokeOneForReview(user, review.reviewId) })
    }

    @Test
    fun `같은 리뷰로 두 번 회수하면 두 번째는 false다`() {
        val user = fixtures.newUser()
        val review = fixtures.newPublishedReview(fixtures.newPlace(), user)
        inTransaction { adapter.grantForReview(user, review.reviewId) }

        assertTrue(inTransaction { adapter.revokeOneForReview(user, review.reviewId) })
        assertFalse(inTransaction { adapter.revokeOneForReview(user, review.reviewId) })
    }

    @Test
    fun `이 리뷰가 발급한 장이 없으면 다른 장으로 회수한다`() {
        // 티켓은 서로 구분되지 않는다 — 잔고가 있으면 회수는 성립한다 (T7)
        val user = fixtures.newUser()
        val granted = fixtures.newPublishedReview(fixtures.newPlace(), user)
        val other = fixtures.newPublishedReview(fixtures.newPlace(), user)
        inTransaction { adapter.grantForReview(user, granted.reviewId) }

        assertTrue(inTransaction { adapter.revokeOneForReview(user, other.reviewId) })
        assertEquals(0, adapter.countAvailable(user))
    }

    @Test
    fun `남의 티켓은 회수되지 않는다`() {
        val me = fixtures.newUser()
        val other = fixtures.newUser()
        val review = fixtures.newPublishedReview(fixtures.newPlace(), other)
        inTransaction { adapter.grantForReview(other, review.reviewId) }

        assertFalse(inTransaction { adapter.revokeOneForReview(me, review.reviewId) })
        assertEquals(1, adapter.countAvailable(other), "남의 잔고는 그대로다")
    }
}
