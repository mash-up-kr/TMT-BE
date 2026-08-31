package com.tmt.application.domain.group

import com.tmt.application.port.input.GroupSort
import com.tmt.application.port.input.GroupsRequest
import com.tmt.application.port.output.persistence.GroupCardRow
import com.tmt.application.port.output.persistence.GroupCardsQuery
import com.tmt.application.port.output.persistence.GroupCardsSlice
import com.tmt.application.port.output.persistence.GroupExplorePort
import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class GroupExploreServiceTest {
    private var lastQuery: GroupCardsQuery? = null
    private var slice = GroupCardsSlice(rows = emptyList(), hasNext = false)
    private var nameExists = false

    private val port =
        object : GroupExplorePort {
            override fun findGroupCards(query: GroupCardsQuery): GroupCardsSlice {
                lastQuery = query
                return slice
            }

            override fun existsByName(name: String) = nameExists
        }

    private val service = GroupExploreService(port, "https://media.example.com/")

    @Test
    fun `정의되지 않은 태그는 GROUP_TAG_NOT_FOUND다`() {
        val food = assertThrows<TmtException> { service.get(request(foodCategoryId = "cat_nope")) }
        assertEquals(ErrorCode.GROUP_TAG_NOT_FOUND, food.errorCode)

        val region = assertThrows<TmtException> { service.get(request(regionTagIds = listOf("region_busan"))) }
        assertEquals(ErrorCode.GROUP_TAG_NOT_FOUND, region.errorCode)
    }

    @Test
    fun `검색어가 태그 라벨에 닿으면 해당 태그 id를 함께 넘긴다 (G18)`() {
        service.get(request(query = "강서"))

        val sent = requireNotNull(lastQuery)
        assertEquals("강서", sent.query)
        assertTrue("region_gangseo" in sent.queryRegionTagIds)
        assertTrue(sent.queryFoodCategoryIds.isEmpty())
    }

    @Test
    fun `빈 검색어는 검색 없음으로 정규화된다`() {
        service.get(request(query = "  "))

        assertNull(requireNotNull(lastQuery).query)
    }

    @Test
    fun `커버는 base-url과 s3_key로 조립되고 없으면 null이다 (G16)`() {
        slice =
            GroupCardsSlice(
                rows =
                    listOf(
                        row(groupId = 1, coverS3Key = "review/a.jpg"),
                        row(groupId = 2, coverS3Key = null),
                    ),
                hasNext = false,
            )

        val result = service.get(request())

        assertEquals("https://media.example.com/review/a.jpg", result.items[0].coverImageUrl)
        assertNull(result.items[1].coverImageUrl)
    }

    @Test
    fun `마지막 키는 정렬 키 그대로다 — 커서 발급의 원천 (TMT-178)`() {
        slice = GroupCardsSlice(rows = listOf(row(groupId = 7, k1 = 3, k2 = 12)), hasNext = true)

        val result = service.get(request())

        val key = requireNotNull(result.lastKey)
        assertEquals(3L, key.k1)
        assertEquals(12L, key.k2)
        assertEquals(7L, key.groupId)
    }

    @Test
    fun `이름 중복 확인은 UNIQUE 제약과 같은 기준이다 (G6)`() {
        nameExists = true
        assertEquals(false, service.isAvailable("성수 커피 탐험대"))
        nameExists = false
        assertEquals(true, service.isAvailable("새 그룹"))
    }

    private fun request(
        query: String? = null,
        foodCategoryId: String? = null,
        regionTagIds: List<String> = emptyList(),
    ) = GroupsRequest(
        viewerId = 1L,
        query = query,
        foodCategoryId = foodCategoryId,
        regionTagIds = regionTagIds,
        sort = GroupSort.RECOMMENDED,
        after = null,
        limit = 20,
    )

    private fun row(
        groupId: Long,
        coverS3Key: String? = null,
        k1: Long = 0,
        k2: Long = 0,
    ) = GroupCardRow(
        groupId = groupId,
        name = "그룹$groupId",
        oneLineDescription = "한줄",
        coverS3Key = coverS3Key,
        memberCount = 1,
        reviewCount = 0,
        placeCount = 0,
        matchedSavedPlaceCount = 0,
        sortKey1 = k1,
        sortKey2 = k2,
    )
}
