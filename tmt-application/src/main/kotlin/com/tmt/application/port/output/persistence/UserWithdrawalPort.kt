package com.tmt.application.port.output.persistence

/**
 * 회원탈퇴의 삭제 경로 (TMT-409). users를 참조하는 FK가 전부 NO ACTION이라
 * **자식부터 정해진 순서로** 지워야 한다 — 순서가 틀리면 그 자리에서 FK 위반으로 멈춘다.
 *
 * 파생 집계는 여기서 건드리지 않는다. 삭제 전에 [findReviewRatings] 등으로 대상을 잡아 두고,
 * 삭제 뒤 기존 집계 포트로 차감한다 — 리뷰 삭제·그룹 탈퇴가 쓰는 계산과 같은 정의를 쓰기 위해서다.
 */
interface UserWithdrawalPort {
    /** 이 사용자가 소유한 그룹. 멤버가 남아 있어도 그룹째 지운다 (G13에서 소유자는 불변이다). */
    fun findOwnedGroupIds(userId: Long): List<Long>

    /** 남의 그룹 중 이 사용자가 ACTIVE 멤버인 곳 — member_count를 차감할 대상. */
    fun findActiveMembershipGroupIds(userId: Long): List<Long>

    /** 삭제되지 않은 내 리뷰의 (매장, 별점) — place 집계를 되돌릴 대상. */
    fun findReviewRatings(userId: Long): List<ReviewRating>

    /**
     * 공유 집합이 바뀌는 남의 그룹 — 내가 공유한 것과 **남이 내 리뷰를 공유한 것**을 함께 본다.
     * 후자를 빠뜨리면 리뷰가 사라진 뒤에도 그 그룹의 review_count가 남는다.
     */
    fun findGroupIdsAffectedByShares(userId: Long): List<Long>

    /** 소유 그룹과 그 하위 행(공유·매장·지역태그·멤버십)을 지운다. */
    fun deleteOwnedGroups(groupIds: List<Long>)

    /** 사용자 본인의 데이터와 users 행을 FK 순서대로 지운다. */
    fun deleteUserData(userId: Long)
}

data class ReviewRating(
    val placeId: Long,
    val rating: Int,
)
