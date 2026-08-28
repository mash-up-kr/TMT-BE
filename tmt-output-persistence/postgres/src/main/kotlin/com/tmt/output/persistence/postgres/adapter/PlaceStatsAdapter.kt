package com.tmt.output.persistence.postgres.adapter

import com.tmt.application.port.output.persistence.PlaceStatsPort
import com.tmt.output.persistence.postgres.repository.PlaceStatsRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class PlaceStatsAdapter(
    private val placeStatsRepository: PlaceStatsRepository,
) : PlaceStatsPort {
    @Transactional
    override fun addReview(
        placeId: Long,
        rating: Int,
    ) {
        placeStatsRepository.addReview(placeId, rating)
    }

    @Transactional
    override fun removeReview(
        placeId: Long,
        rating: Int,
    ) {
        placeStatsRepository.removeReview(placeId, rating)
    }
}
