package com.tmt.application.domain.recommendation

import com.tmt.application.domain.media.MediaUrlResolver
import com.tmt.application.domain.place.FoodCategories
import com.tmt.application.port.input.GetReviewedPlacesUseCase
import com.tmt.application.port.input.PlaceRecommendationCommand
import com.tmt.application.port.input.PlaceRecommendationView
import com.tmt.application.port.input.RecommendPlaceUseCase
import com.tmt.application.port.input.ReviewedPlaceKey
import com.tmt.application.port.input.ReviewedPlaceSlice
import com.tmt.application.port.input.ReviewedPlaceView
import com.tmt.application.port.output.llm.PlaceChoiceRequest
import com.tmt.application.port.output.llm.PlaceRecommendationLlmPort
import com.tmt.application.port.output.persistence.RecommendationQueryPort
import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

/**
 * 매장 추천 (TMT-289, 명세 v2 J §5). 사용자가 격자에서 고른 매장을 근거로
 * **아직 리뷰하지 않은** 매장 1곳을 고른다.
 *
 * 후보 추리기는 SQL이, 그중 하나를 고르는 것은 LLM이 한다. 후보를 서버가 자르는 이유는
 * 프롬프트 크기와 비용이 매장 수에 비례하지 않게 하기 위한 것이고, 고르는 것을 LLM에 맡기는
 * 이유는 같은 재료로도 호출마다 결과가 달라져야 `재추천` 버튼이 동작하기 때문이다 (§5-2).
 */
@Service
class PlaceRecommendationService(
    private val recommendationQueryPort: RecommendationQueryPort,
    private val placeRecommendationLlmPort: PlaceRecommendationLlmPort,
    private val mediaUrlResolver: MediaUrlResolver,
) : GetReviewedPlacesUseCase,
    RecommendPlaceUseCase {
    @Transactional(readOnly = true)
    override fun list(
        userId: Long,
        after: ReviewedPlaceKey?,
        limit: Int,
    ): ReviewedPlaceSlice {
        val rows =
            recommendationQueryPort.findReviewedPlaceRows(
                userId = userId,
                afterLatestReviewedAt = after?.latestReviewedAt,
                afterPlaceId = after?.placeId,
                limitPlusOne = limit + 1,
            )
        return ReviewedPlaceSlice(
            items =
                rows.take(limit).map {
                    ReviewedPlaceView(
                        placeId = it.placeId,
                        name = it.name,
                        categoryId = it.categoryId,
                        thumbnailUrl = it.thumbnailS3Key?.let(mediaUrlResolver::urlOf),
                        latestReviewedAt = it.latestReviewedAt,
                    )
                },
            hasNext = rows.size > limit,
        )
    }

    /**
     * LLM 호출이 있어 트랜잭션으로 감싸지 않는다 — 외부 응답을 기다리는 동안 DB 커넥션을
     * 붙잡으면 t3.micro의 작은 풀이 먼저 마른다. 읽기 세 번이 각자의 트랜잭션이고,
     * 그 사이 데이터가 바뀌어도 추천 한 건이 살짝 어긋날 뿐이라 감수한다.
     */
    override fun recommend(command: PlaceRecommendationCommand): PlaceRecommendationView {
        val seedPlaceIds = validated(command.seedPlaceIds)
        verifyAllReviewedByViewer(command.userId, seedPlaceIds)

        val candidates = recommendationQueryPort.findCandidatePlaces(command.userId, seedPlaceIds, CANDIDATE_LIMIT)
        // 리뷰는 있는데 안 가본 매장이 없는 경우다 — 화면은 "추천할 매장이 없어요"로 같게 그린다 (§5-2)
        if (candidates.isEmpty()) throw TmtException(ErrorCode.RECOMMENDATION_UNAVAILABLE)

        val seeds = recommendationQueryPort.findSeedPlaces(command.userId, seedPlaceIds)
        val chosenId =
            runCatching {
                placeRecommendationLlmPort.choose(
                    PlaceChoiceRequest(
                        seeds =
                            seeds.map {
                                PlaceChoiceRequest.Seed(
                                    name = it.name,
                                    categoryId = it.categoryId,
                                    rating = it.rating,
                                    content = it.content,
                                )
                            },
                        candidates =
                            candidates.map {
                                PlaceChoiceRequest.Candidate(
                                    placeId = it.placeId,
                                    name = it.name,
                                    categoryId = it.categoryId,
                                    regionName = it.regionName,
                                    averageRating = it.averageRating,
                                )
                            },
                    ),
                )
            }.getOrElse { e ->
                // 설정 결함(키 없음)까지 503으로 덮으면 "잠시 뒤 다시"로 읽혀 원인이 묻힌다
                if (e is TmtException) throw e
                logger.warn(e) { "매장 추천 LLM 실패 - userId=${command.userId}, candidates=${candidates.size}" }
                throw TmtException(ErrorCode.RECOMMENDATION_FAILED)
            }

        // 후보에 없는 id는 어댑터가 이미 걸러 예외로 만든다. 여기 도달했는데 없다면 우리 결함이다
        val row =
            recommendationQueryPort.findRecommendedPlace(chosenId)
                ?: run {
                    logger.warn { "추천된 매장을 다시 읽지 못했다 - placeId=$chosenId" }
                    throw TmtException(ErrorCode.RECOMMENDATION_FAILED)
                }

        return PlaceRecommendationView(
            placeId = row.placeId,
            name = row.name,
            roadAddress = row.roadAddress,
            categoryName = FoodCategories.labelOf(row.categoryId),
            thumbnailUrl = row.thumbnailS3Key?.let(mediaUrlResolver::urlOf),
            // 요약은 매장 최신 리뷰의 것을 그대로 쓴다 (A3). 리뷰가 없거나 요약 전이면 통째로 null (A2)
            summary =
                row.summaryReviewId?.let {
                    PlaceRecommendationView.Summary(
                        reviewId = it,
                        pros = row.summaryPros,
                        cons = row.summaryCons,
                    )
                },
        )
    }

    /**
     * 화면의 버튼 비활성은 요청이 오지 않는다는 보장이 아니다 — 서버가 같은 범위를 다시 본다.
     * 상한이 없으면 LLM 호출 비용이 요청자 마음대로 늘어난다 (§5-2).
     */
    private fun validated(seedPlaceIds: List<Long>): List<Long> {
        if (seedPlaceIds.size != seedPlaceIds.distinct().size) {
            throw TmtException(ErrorCode.VALIDATION_FAILED, "placeIds에 같은 매장이 두 번 들어 있습니다.")
        }
        if (seedPlaceIds.size !in SEED_MIN..SEED_MAX) {
            throw TmtException(ErrorCode.VALIDATION_FAILED, "placeIds는 ${SEED_MIN}~${SEED_MAX}개여야 합니다.")
        }
        return seedPlaceIds
    }

    /**
     * 없는 매장과 내가 리뷰하지 않은 매장을 가르지 않는다 — 가르면 "이 매장이 존재하는가"를
     * 응답이 흘린다. 화면은 어느 쪽이든 격자를 다시 그리면 된다 (A4, §5-2).
     */
    private fun verifyAllReviewedByViewer(
        userId: Long,
        seedPlaceIds: List<Long>,
    ) {
        val reviewed = recommendationQueryPort.findReviewedPlaceIdsAmong(userId, seedPlaceIds).toSet()
        if (reviewed.size != seedPlaceIds.size) throw TmtException(ErrorCode.PLACE_NOT_FOUND)
    }

    companion object {
        /** 화면의 카운터 `1/5`와 버튼 비활성 기준 (§5-2) */
        const val SEED_MIN = 2
        const val SEED_MAX = 5

        /**
         * LLM에 넘길 후보 수. 프롬프트가 이 값에 비례하므로 무한정 늘리지 않는다 —
         * 30곳이면 고를 여지는 충분하고 입력 토큰은 수백 수준이다.
         */
        const val CANDIDATE_LIMIT = 30
    }
}
