package com.tmt.application.port.output.persistence

/** 그룹 가입 티켓 발급 (T6·T8). 발급 근거(reward_grant) 1건당 티켓 1장이다. */
interface GroupJoinTicketPort {
    fun countAvailable(userId: Long): Int

    /** 리뷰 1건을 근거로 티켓 1장을 발급한다. 같은 리뷰로 두 번 부르면 UNIQUE 제약이 막는다 (T8). */
    fun grantForReview(
        userId: Long,
        reviewId: Long,
    )

    /**
     * 리뷰 삭제로 티켓 1장을 회수한다 (R7, TX-5). 회수했으면 true, 회수할 `AVAILABLE` 티켓이
     * 없으면 false다 — 이미 그룹 가입에 쓴 티켓은 되돌릴 수 없다.
     *
     * 구현은 조건부 UPDATE 한 문장이어야 한다. 읽어서 고르고 쓰면 동시 요청이 같은 티켓을
     * 두 번 회수한다. 티켓은 서로 구분되지 않지만 이 리뷰가 발급한 장을 먼저 고른다.
     */
    fun revokeOneForReview(
        userId: Long,
        reviewId: Long,
    ): Boolean

    /**
     * 그룹 가입으로 티켓 1장을 소비한다 (T3·T7, TX-3). 소비했으면 true, `AVAILABLE` 티켓이
     * 없으면 false다. 발급 오래된 순으로 고른다.
     *
     * 구현은 조건부 UPDATE 한 문장이어야 한다 — 읽어서 고르고 쓰면 동시 요청이 같은 티켓을
     * 두 번 소비한다.
     */
    fun consumeOne(
        userId: Long,
        groupId: Long,
    ): Boolean
}
