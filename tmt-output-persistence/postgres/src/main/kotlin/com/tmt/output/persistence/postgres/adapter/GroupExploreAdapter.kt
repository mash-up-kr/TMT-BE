package com.tmt.output.persistence.postgres.adapter

import com.tmt.application.port.input.GroupListKey
import com.tmt.application.port.output.persistence.GroupCardRow
import com.tmt.application.port.output.persistence.GroupCardsQuery
import com.tmt.application.port.output.persistence.GroupCardsSlice
import com.tmt.application.port.output.persistence.GroupExplorePort
import com.tmt.output.persistence.postgres.repository.GroupExploreRepository
import com.tmt.output.persistence.postgres.repository.LikePatterns
import org.springframework.stereotype.Component

@Component
class GroupExploreAdapter(
    private val repository: GroupExploreRepository,
) : GroupExplorePort {
    override fun findGroupCards(query: GroupCardsQuery): GroupCardsSlice {
        val rows =
            repository.findGroupCards(
                viewerId = query.viewerId,
                queryPattern = LikePatterns.contains(query.query),
                queryFoodCsv = query.queryFoodCategoryIds.toCsvOrNull(),
                queryRegionCsv = query.queryRegionTagIds.toCsvOrNull(),
                foodCategoryId = query.foodCategoryId,
                regionCsv = query.regionTagIds.toCsvOrNull(),
                sort = query.sort,
                afterK1 = query.after?.k1,
                afterK2 = query.after?.k2,
                afterGroupId = query.after?.groupId,
                limitPlusOne = query.limit + 1,
            )
        val page = rows.take(query.limit)
        return GroupCardsSlice(
            rows =
                page.map {
                    GroupCardRow(
                        groupId = it.getGroupId(),
                        name = it.getName(),
                        oneLineDescription = it.getOneLineDescription(),
                        coverS3Key = it.getCoverS3Key(),
                        memberCount = it.getMemberCount(),
                        reviewCount = it.getReviewCount(),
                        placeCount = it.getPlaceCount(),
                        matchedSavedPlaceCount = it.getMatchedSavedPlaceCount().toInt(),
                    )
                },
            hasNext = rows.size > query.limit,
            lastKey = page.lastOrNull()?.let { GroupListKey(it.getSortKey1(), it.getSortKey2(), it.getGroupId()) },
        )
    }

    override fun existsByName(name: String): Boolean = repository.existsByName(name)

    /** 태그 id는 상수 코드(콤마 없음)라 CSV로 안전하다. 빈 목록은 null — 쿼리가 분기를 건너뛴다. */
    private fun List<String>.toCsvOrNull(): String? = takeIf { it.isNotEmpty() }?.joinToString(",")
}
