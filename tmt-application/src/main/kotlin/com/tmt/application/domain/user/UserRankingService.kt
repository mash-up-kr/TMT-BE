package com.tmt.application.domain.user

import com.tmt.application.domain.media.MediaUrlResolver
import com.tmt.application.port.input.GetUserRankingsUseCase
import com.tmt.application.port.input.UserRankingView
import com.tmt.application.port.input.UserRankingsRequest
import com.tmt.application.port.input.UserRankingsResult
import com.tmt.application.port.output.persistence.UserRankingQueryPort
import com.tmt.application.port.output.persistence.UserRankingsQuery
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 유저 활동량 랭킹 (TMT-436). */
@Service
@Transactional(readOnly = true)
class UserRankingService(
    private val userRankingQueryPort: UserRankingQueryPort,
    private val mediaUrlResolver: MediaUrlResolver,
) : GetUserRankingsUseCase {
    override fun get(request: UserRankingsRequest): UserRankingsResult {
        val slice =
            userRankingQueryPort.findUserRankings(
                UserRankingsQuery(after = request.after, limit = request.limit),
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
