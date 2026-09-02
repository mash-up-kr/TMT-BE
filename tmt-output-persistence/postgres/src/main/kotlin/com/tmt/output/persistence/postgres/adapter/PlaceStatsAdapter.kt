package com.tmt.output.persistence.postgres.adapter

import com.tmt.application.port.output.persistence.PlaceStatsPort
import com.tmt.output.persistence.postgres.repository.PlaceStatsRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class PlaceStatsAdapter(
    private val placeStatsRepository: PlaceStatsRepository,
) : PlaceStatsPort {
    // 대상 매장은 호출부가 저장·리뷰로 이미 확인했고 FK가 존재를 보장한다
    @Transactional
    override fun addReview(
        placeId: Long,
        rating: Int,
    ) {
        placeStatsRepository.addReview(placeId, rating)
    }

    /**
     * 0행이면 집계가 이미 어긋나 있다는 뜻이다 — 음수 방지 조건(`reviewCount > 0`)이 차감을 막은 것이라
     * 되돌릴 대상이 없다. 리뷰 삭제 자체는 성공해야 하므로 여기서 끊지 않는다 (P9·E6).
     */
    @Transactional
    override fun removeReview(
        placeId: Long,
        rating: Int,
    ) {
        placeStatsRepository.removeReview(placeId, rating)
    }
}
