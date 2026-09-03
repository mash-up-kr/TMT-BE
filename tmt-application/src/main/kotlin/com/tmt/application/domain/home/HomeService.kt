package com.tmt.application.domain.home

import com.tmt.application.domain.review.ReviewCardComposer
import com.tmt.application.port.input.GetHomeFeedUseCase
import com.tmt.application.port.input.GetHomeUseCase
import com.tmt.application.port.input.GroupCardView
import com.tmt.application.port.input.GroupSort
import com.tmt.application.port.input.HomeFeedRequest
import com.tmt.application.port.input.HomeFeedResult
import com.tmt.application.port.input.HomeResult
import com.tmt.application.port.output.persistence.GroupCardRow
import com.tmt.application.port.output.persistence.GroupCardsQuery
import com.tmt.application.port.output.persistence.GroupExplorePort
import com.tmt.application.port.output.persistence.HomeQueryPort
import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException
import org.springframework.stereotype.Service

@Service
class HomeService(
    private val homeQueryPort: HomeQueryPort,
    // 추천 캐러셀은 그룹 탐색의 추천순(G17)과 같은 목록이다 — 홈이 규칙을 다시 구현하지 않고 그룹 포트에 묻는다 (TMT-305)
    private val groupExplorePort: GroupExplorePort,
    private val reviewCardComposer: ReviewCardComposer,
) : GetHomeUseCase,
    GetHomeFeedUseCase {
    override fun get(viewerId: Long): HomeResult {
        val nickname = homeQueryPort.findNickname(viewerId) ?: throw TmtException(ErrorCode.USER_NOT_FOUND)
        return HomeResult(
            nickname = nickname,
            myGroups =
                homeQueryPort.findMyGroups(viewerId).map {
                    HomeResult.MyGroup(
                        groupId = it.groupId,
                        name = it.name,
                        imageUrl = it.imageS3Key?.let(reviewCardComposer::mediaUrl),
                    )
                },
            // 가입 그룹이 0개여도 추천은 채운다 — 신규 사용자의 홈이 이 캐러셀 하나로 성립한다 (A §0)
            recommendedGroups = recommendedGroups(viewerId),
        )
    }

    override fun get(request: HomeFeedRequest): HomeFeedResult {
        val latitude = request.latitude
        val longitude = request.longitude
        // 좌표는 둘 다이거나 둘 다 없거나다 (A 명세 §3). 반쪽을 조용히 최신순으로 떨어뜨리면
        // 클라이언트 버그가 정렬이 바뀐 채로 묻힌다 — 근처 탐색도 같은 상황에서 400이다
        if ((latitude == null) != (longitude == null)) {
            throw TmtException(ErrorCode.VALIDATION_FAILED, "latitude·longitude는 함께 보내야 합니다.")
        }
        val sortedByDistance = latitude != null && longitude != null
        if (sortedByDistance && (latitude !in -90.0..90.0 || longitude !in -180.0..180.0)) {
            throw TmtException(ErrorCode.VALIDATION_FAILED, "latitude·longitude는 위경도 범위 안이어야 합니다.")
        }

        val page =
            if (sortedByDistance) {
                homeQueryPort.findFeedRowsByDistance(
                    userId = request.viewerId,
                    latitude = requireNotNull(latitude),
                    longitude = requireNotNull(longitude),
                    afterDistanceMeters = request.after?.distanceMeters,
                    afterReviewId = request.after?.reviewId,
                    limit = request.limit,
                )
            } else {
                homeQueryPort.findFeedRowsByRecency(
                    userId = request.viewerId,
                    afterCreatedAt = request.after?.createdAt,
                    afterReviewId = request.after?.reviewId,
                    limit = request.limit,
                )
            }

        return HomeFeedResult(
            items = reviewCardComposer.compose(page.rows),
            hasNext = page.hasNext,
            sortedByDistance = sortedByDistance,
        )
    }

    /**
     * `혹시, 이런 그룹은 어떠세요?` 캐러셀 — 그룹 탐색의 **추천순 첫 페이지에서 가입한 그룹만 뺀 것**이다 (A §5-3).
     * 정렬(G17)도 커버(G16)도 그룹 쪽 규칙이라 홈이 따로 구현하지 않는다 — 예전에는 같은 SQL이 복사돼 있었고,
     * 한쪽만 고쳐지면 같은 그룹이 홈과 탐색에서 다른 순서·다른 커버로 보였다 (TMT-305).
     */
    private fun recommendedGroups(viewerId: Long): List<GroupCardView> =
        groupExplorePort
            .findGroupCards(
                GroupCardsQuery(
                    viewerId = viewerId,
                    query = null,
                    queryFoodCategoryIds = emptyList(),
                    queryRegionTagIds = emptyList(),
                    foodCategoryId = null,
                    regionTagIds = emptyList(),
                    sort = GroupSort.RECOMMENDED.name,
                    excludeJoinedBy = viewerId,
                    after = null,
                    limit = RECOMMENDED_COUNT,
                ),
            ).rows
            .map(::toCardView)

    private fun toCardView(row: GroupCardRow) =
        GroupCardView(
            groupId = row.groupId,
            name = row.name,
            oneLineDescription = row.oneLineDescription,
            coverImageUrl = row.coverS3Key?.let(reviewCardComposer::mediaUrl),
            memberCount = row.memberCount,
            reviewCount = row.reviewCount,
            placeCount = row.placeCount,
            matchedSavedPlaceCount = row.matchedSavedPlaceCount,
        )

    companion object {
        /** 혹시, 이런 그룹은 어떠세요? 캐러셀 — 상위 5개 (A §2) */
        const val RECOMMENDED_COUNT = 5
    }
}
