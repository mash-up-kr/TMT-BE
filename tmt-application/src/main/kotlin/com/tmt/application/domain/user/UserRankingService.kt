package com.tmt.application.domain.user

import com.tmt.application.domain.media.MediaUrlResolver
import com.tmt.application.port.input.GetUserRankingsUseCase
import com.tmt.application.port.input.UserRankingView
import com.tmt.application.port.input.UserRankingsRequest
import com.tmt.application.port.input.UserRankingsResult
import com.tmt.application.port.output.persistence.UserRankingQueryPort
import com.tmt.application.port.output.persistence.UserRankingsQuery
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 유저 활동량 랭킹 (TMT-436).
 *
 * 팀원 테스트 계정과 시드 계정은 뺀다 — 런칭 전에 리뷰를 채운 계정이 상위를 차지하면 리더보드가
 * 의미를 잃는다. 목록은 설정 `tmt.ranking.excluded-user-ids`(쉼표 구분 id)라 배포 없이 환경변수로
 * 바꾼다. 닉네임이 아니라 id로 거른다 — 닉네임은 사용자가 바꿀 수 있다.
 */
@Service
@Transactional(readOnly = true)
class UserRankingService(
    private val userRankingQueryPort: UserRankingQueryPort,
    private val mediaUrlResolver: MediaUrlResolver,
    @param:Value("\${tmt.ranking.excluded-user-ids:}") excludedUserIdsCsv: String = "",
) : GetUserRankingsUseCase {
    private val excludedUserIds: List<Long> =
        excludedUserIdsCsv.split(',').mapNotNull { it.trim().toLongOrNull() }

    override fun get(request: UserRankingsRequest): UserRankingsResult {
        val slice =
            userRankingQueryPort.findUserRankings(
                UserRankingsQuery(
                    sort = request.sort,
                    after = request.after,
                    limit = request.limit,
                    excludedUserIds = excludedUserIds,
                ),
            )

        return UserRankingsResult(
            items =
                slice.rows.map { row ->
                    UserRankingView(
                        userId = row.userId,
                        nickname = row.nickname,
                        profileImageUrl = row.profileImageS3Key?.let(mediaUrlResolver::urlOf) ?: row.profileImageUrl,
                        reviewCount = row.reviewCount,
                        sharedReviewCount = row.sharedReviewCount,
                    )
                },
            hasNext = slice.hasNext,
            lastKey = slice.lastKey,
        )
    }
}
