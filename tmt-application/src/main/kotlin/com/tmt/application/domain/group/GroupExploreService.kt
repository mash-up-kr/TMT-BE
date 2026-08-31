package com.tmt.application.domain.group

import com.tmt.application.port.input.CheckGroupNameUseCase
import com.tmt.application.port.input.GetGroupsUseCase
import com.tmt.application.port.input.GroupCardView
import com.tmt.application.port.input.GroupsRequest
import com.tmt.application.port.input.GroupsResult
import com.tmt.application.port.output.persistence.GroupCardsQuery
import com.tmt.application.port.output.persistence.GroupExplorePort
import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 그룹 탐색 (D_01, TMT-220) — 검색·필터·정렬이 모두 한 목록에 걸린다. */
@Service
@Transactional(readOnly = true)
class GroupExploreService(
    private val groupExplorePort: GroupExplorePort,
    @param:Value("\${tmt.media.base-url:}") private val mediaBaseUrl: String,
) : GetGroupsUseCase,
    CheckGroupNameUseCase {
    override fun get(request: GroupsRequest): GroupsResult {
        request.foodCategoryId?.let {
            if (it !in GroupTagCatalog.FOOD_CATEGORY_IDS) throw TmtException(ErrorCode.GROUP_TAG_NOT_FOUND, it)
        }
        request.regionTagIds.forEach {
            if (it !in GroupTagCatalog.REGION_TAG_IDS) throw TmtException(ErrorCode.GROUP_TAG_NOT_FOUND, it)
        }

        val query = request.query?.trim()?.takeIf { it.isNotEmpty() }
        val slice =
            groupExplorePort.findGroupCards(
                GroupCardsQuery(
                    viewerId = request.viewerId,
                    query = query,
                    queryFoodCategoryIds = query?.let(GroupTagCatalog::foodIdsMatching).orEmpty(),
                    queryRegionTagIds = query?.let(GroupTagCatalog::regionIdsMatching).orEmpty(),
                    foodCategoryId = request.foodCategoryId,
                    regionTagIds = request.regionTagIds,
                    sort = request.sort.name,
                    after = request.after,
                    limit = request.limit,
                ),
            )

        return GroupsResult(
            items =
                slice.rows.map { row ->
                    GroupCardView(
                        groupId = row.groupId,
                        name = row.name,
                        oneLineDescription = row.oneLineDescription,
                        // 공개 읽기 버킷 (TMT-201) — base-url + s3_key가 곧 조회 URL이다
                        coverImageUrl = row.coverS3Key?.let { "${mediaBaseUrl.trimEnd('/')}/$it" },
                        memberCount = row.memberCount,
                        reviewCount = row.reviewCount,
                        placeCount = row.placeCount,
                        matchedSavedPlaceCount = row.matchedSavedPlaceCount,
                        sortKey1 = row.sortKey1,
                        sortKey2 = row.sortKey2,
                    )
                },
            hasNext = slice.hasNext,
        )
    }

    override fun isAvailable(name: String): Boolean = !groupExplorePort.existsByName(name)
}
