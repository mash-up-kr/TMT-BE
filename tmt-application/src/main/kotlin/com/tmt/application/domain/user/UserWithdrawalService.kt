package com.tmt.application.domain.user

import com.tmt.application.port.input.WithdrawUserUseCase
import com.tmt.application.port.output.persistence.GroupStatsPort
import com.tmt.application.port.output.persistence.PlaceStatsPort
import com.tmt.application.port.output.persistence.UserAccountPort
import com.tmt.application.port.output.persistence.UserWithdrawalPort
import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 회원탈퇴 (TMT-409). 삭제와 집계 보정이 한 트랜잭션이다 — 중간에 끊기면 지표가 사실과 어긋난다.
 *
 * 집계는 **전체 재계산이 아니라 이 사용자 몫의 차감**이다. 리뷰 삭제(P9)와 그룹 탈퇴(G17)가 쓰는
 * 포트를 그대로 부르므로 `deleted_at IS NULL` 같은 조건이 한 곳에만 있다.
 *
 * **소유 그룹은 멤버가 남아 있어도 그룹째 지운다** — G13에서 owner_id가 불변이라 넘길 자리가 없다.
 * 그 그룹에 남이 공유해 둔 리뷰는 공유 연결만 끊기고 리뷰 자체는 남는다.
 *
 * S3 객체는 지우지 않는다 — media_asset 행만 지운다.
 */
@Service
class UserWithdrawalService(
    private val userAccountPort: UserAccountPort,
    private val userWithdrawalPort: UserWithdrawalPort,
    private val placeStatsPort: PlaceStatsPort,
    private val groupStatsPort: GroupStatsPort,
) : WithdrawUserUseCase {
    @Transactional
    override fun withdraw(userId: Long) {
        userAccountPort.findById(userId) ?: throw TmtException(ErrorCode.USER_NOT_FOUND)

        // 지우기 전에 잡아 둔다 — 삭제 뒤에는 차감할 대상을 알 방법이 없다
        val ownedGroupIds = userWithdrawalPort.findOwnedGroupIds(userId)
        val memberGroupIds = userWithdrawalPort.findActiveMembershipGroupIds(userId) - ownedGroupIds.toSet()
        val reviewRatings = userWithdrawalPort.findReviewRatings(userId)
        val shareGroupIds = userWithdrawalPort.findGroupIdsAffectedByShares(userId) - ownedGroupIds.toSet()

        userWithdrawalPort.deleteOwnedGroups(ownedGroupIds)
        userWithdrawalPort.deleteUserData(userId)

        reviewRatings.forEach { placeStatsPort.removeReview(it.placeId, it.rating) }
        memberGroupIds.forEach { groupStatsPort.removeMember(it) }
        // 공유 집합이 바뀐 그룹은 다시 센다 — 매장 중복 때문에 증감만으로는 place_count가 어긋난다
        shareGroupIds.forEach { groupStatsPort.refreshShareStats(it) }
    }
}
