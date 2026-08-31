package com.tmt.application.port.output.persistence

/** 그룹 가입 티켓 발급 (T6·T8). 발급 근거(reward_grant) 1건당 티켓 1장이다. */
interface GroupJoinTicketPort {
    fun countAvailable(userId: Long): Int

    /** 리뷰 1건을 근거로 티켓 1장을 발급한다. 같은 리뷰로 두 번 부르면 UNIQUE 제약이 막는다 (T8). */
    fun grantForReview(
        userId: Long,
        reviewId: Long,
    )

    /** 가입 보상 1장 (T2). 같은 사용자로 두 번 부르면 UNIQUE 제약이 막는다 (T8). */
    fun grantForSignup(userId: Long)
}
