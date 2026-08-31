package com.tmt.input.http.controller

import com.tmt.application.port.input.CheckGroupNameUseCase
import com.tmt.application.port.input.GetGroupsUseCase
import com.tmt.application.port.input.GroupCardView
import com.tmt.application.port.input.GroupsRequest
import com.tmt.application.port.input.GroupsResult
import com.tmt.input.http.auth.UserIdArgumentResolver
import com.tmt.input.http.exception.ExceptionAdvice
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

/**
 * 그룹 탐색 실구현의 어댑터 계약 — 응답 형태·ID 표기·커서 왕복·정렬 검증이 mock과 같은지 지킨다.
 */
class GroupExploreControllerTest {
    private var lastRequest: GroupsRequest? = null
    private var result = GroupsResult(items = emptyList(), hasNext = false)
    private var available = true

    private val getGroups =
        GetGroupsUseCase { request ->
            lastRequest = request
            result
        }

    private val checkName = CheckGroupNameUseCase { available }

    private val mockMvc: MockMvc =
        MockMvcBuilders
            .standaloneSetup(GroupExploreController(getGroups, checkName))
            .setCustomArgumentResolvers(UserIdArgumentResolver())
            .setControllerAdvice(ExceptionAdvice())
            .build()

    @Test
    fun `그룹 카드가 mock과 같은 접두 ID 표기로 나간다`() {
        result = GroupsResult(items = listOf(card(groupId = 3)), hasNext = false)

        mockMvc
            .perform(get("/v1/groups").header("X-User-Id", "1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items[0].groupId").value("group_3"))
            .andExpect(jsonPath("$.items[0].matchedSavedPlaceCount").value(2))
            .andExpect(jsonPath("$.hasNext").value(false))
    }

    @Test
    fun `지원하지 않는 sort 값은 VALIDATION_FAILED다`() {
        mockMvc
            .perform(get("/v1/groups").param("sort", "POPULAR"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
    }

    @Test
    fun `다음 페이지가 있으면 커서를 발급하고 같은 조건에서 되읽는다`() {
        result = GroupsResult(items = listOf(card(groupId = 5, k1 = 2, k2 = 9)), hasNext = true)

        val body =
            mockMvc
                .perform(get("/v1/groups").header("X-User-Id", "1"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.hasNext").value(true))
                .andReturn()
                .response.contentAsString
        val cursor = Regex("\"nextCursor\":\"([^\"]+)\"").find(body)!!.groupValues[1]
        assertNotNull(cursor)

        mockMvc
            .perform(get("/v1/groups").header("X-User-Id", "1").param("cursor", cursor))
            .andExpect(status().isOk)

        val after = requireNotNull(requireNotNull(lastRequest).after)
        assertEquals(2L, after.k1)
        assertEquals(9L, after.k2)
        assertEquals(5L, after.groupId)
    }

    @Test
    fun `조건이 바뀌면 이전 커서는 무효다`() {
        result = GroupsResult(items = listOf(card(groupId = 5)), hasNext = true)
        val body =
            mockMvc
                .perform(get("/v1/groups").header("X-User-Id", "1"))
                .andReturn()
                .response.contentAsString
        val cursor = Regex("\"nextCursor\":\"([^\"]+)\"").find(body)!!.groupValues[1]

        // 필터 변경
        mockMvc
            .perform(
                get("/v1/groups").header("X-User-Id", "1").param("foodCategoryId", "cat_meat").param("cursor", cursor),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_CURSOR"))

        // 추천순은 조회자도 조건이다 — 조회자가 바뀌면 무효
        mockMvc
            .perform(get("/v1/groups").header("X-User-Id", "2").param("cursor", cursor))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_CURSOR"))
    }

    @Test
    fun `이름 중복 확인은 인증 필수이고 참고값을 돌려준다`() {
        available = false
        mockMvc
            .perform(get("/v1/groups/name-availability").header("X-User-Id", "1").param("name", "성수 커피 탐험대"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.available").value(false))

        mockMvc
            .perform(get("/v1/groups/name-availability").param("name", "성수 커피 탐험대"))
            .andExpect(status().isUnauthorized)

        mockMvc
            .perform(get("/v1/groups/name-availability").header("X-User-Id", "1"))
            .andExpect(status().isBadRequest)
    }

    private fun card(
        groupId: Long,
        k1: Long = 0,
        k2: Long = 0,
    ) = GroupCardView(
        groupId = groupId,
        name = "그룹$groupId",
        oneLineDescription = "한줄",
        coverImageUrl = null,
        memberCount = 4,
        reviewCount = 3,
        placeCount = 2,
        matchedSavedPlaceCount = 2,
        sortKey1 = k1,
        sortKey2 = k2,
    )
}
