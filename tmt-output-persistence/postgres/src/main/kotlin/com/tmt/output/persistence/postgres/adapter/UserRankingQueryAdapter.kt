package com.tmt.output.persistence.postgres.adapter

import com.tmt.application.port.input.UserRankingKey
import com.tmt.application.port.output.persistence.UserRankingQueryPort
import com.tmt.application.port.output.persistence.UserRankingRow
import com.tmt.application.port.output.persistence.UserRankingsQuery
import com.tmt.application.port.output.persistence.UserRankingsSlice
import com.tmt.output.persistence.postgres.repository.UserRankingQueryRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
@Transactional(readOnly = true)
class UserRankingQueryAdapter(
    private val userRankingQueryRepository: UserRankingQueryRepository,
) : UserRankingQueryPort {
    override fun findUserRankings(query: UserRankingsQuery): UserRankingsSlice {
        val rows =
            userRankingQueryRepository.findUserRankingRows(
                afterReviewCount = query.after?.reviewCount,
                afterUserId = query.after?.userId,
                limitPlusOne = query.limit + 1,
            )
        val page = rows.take(query.limit)
        return UserRankingsSlice(
            rows =
                page.map {
                    UserRankingRow(
                        userId = it.getUserId(),
                        nickname = it.getNickname(),
                        profileImageUrl = it.getProfileImageUrl(),
                        profileImageS3Key = it.getProfileImageS3Key(),
                        reviewCount = it.getReviewCount(),
                        sharedReviewCount = it.getSharedReviewCount(),
                    )
                },
            hasNext = rows.size > query.limit,
            lastKey = page.lastOrNull()?.let { UserRankingKey(it.getReviewCount(), it.getUserId()) },
        )
    }
}
