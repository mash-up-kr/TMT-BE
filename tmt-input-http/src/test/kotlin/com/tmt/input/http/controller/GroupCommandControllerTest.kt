package com.tmt.input.http.controller

import com.tmt.application.port.input.CreateGroupUseCase
import com.tmt.application.port.input.GroupCommand
import com.tmt.application.port.input.GroupDetailView
import com.tmt.application.port.input.ImageAssetSelection
import com.tmt.application.port.input.UpdateGroupUseCase
import com.tmt.input.http.auth.UserIdArgumentResolver
import com.tmt.input.http.exception.ExceptionAdvice
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

/** 그룹 생성·편집 실구현의 어댑터 계약 — 응답 형태·ID 표기·assetId 파싱이 mock과 같은지 지킨다. */
class GroupCommandControllerTest {
    private var lastCreate: GroupCommand? = null
    private var lastUpdate: Pair<Long, GroupCommand>? = null
    private var view = view()

    private val createUseCase =
        CreateGroupUseCase { command ->
            lastCreate = command
            view
        }
    private val updateUseCase =
        UpdateGroupUseCase { groupId, command ->
            lastUpdate = groupId to command
            view
        }

    private val mockMvc: MockMvc =
        MockMvcBuilders
            .standaloneSetup(GroupCommandController(createUseCase, updateUseCase))
            .setCustomArgumentResolvers(UserIdArgumentResolver())
            .setControllerAdvice(ExceptionAdvice())
            .build()

    @Test
    fun `생성은 201과 Location, mock과 같은 상세 형태로 응답한다`() {
        mockMvc
            .perform(
                post("/v1/groups")
                    .requestAttr(UserIdArgumentResolver.USER_ID_ATTRIBUTE, 1L)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(imageAssetId = "\"42\"")),
            ).andExpect(status().isCreated)
            .andExpect(header().string("Location", "/v1/groups/group_10"))
            .andExpect(jsonPath("$.groupId").value("group_10"))
            .andExpect(jsonPath("$.foodCategory.label").value("한식"))
            .andExpect(jsonPath("$.regionTags[0].label").value("구로구"))
            .andExpect(jsonPath("$.coverImages[0].reviewId").value("rv_3"))
            .andExpect(jsonPath("$.isOwner").value(true))

        assertEquals(ImageAssetSelection.Set(42L), requireNotNull(lastCreate).imageAsset)
    }

    @Test
    fun `접두가 붙은 옛 assetId는 없는 사진과 같다 (M2)`() {
        mockMvc
            .perform(
                post("/v1/groups")
                    .requestAttr(UserIdArgumentResolver.USER_ID_ATTRIBUTE, 1L)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(imageAssetId = "\"asset_42\"")),
            ).andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("MEDIA_NOT_OWNED"))
    }

    @Test
    fun `assetId 자리에 객체가 오면 형식 오류로 400이다`() {
        mockMvc
            .perform(
                post("/v1/groups")
                    .requestAttr(UserIdArgumentResolver.USER_ID_ATTRIBUTE, 1L)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(imageAssetId = "{\"id\":42}")),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
    }

    @Test
    fun `편집은 group_ 접두 ID를 풀어 넘기고, 형식이 어긋나면 GROUP_NOT_FOUND다`() {
        mockMvc
            .perform(
                put("/v1/groups/group_7")
                    .requestAttr(UserIdArgumentResolver.USER_ID_ATTRIBUTE, 1L)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body()),
            ).andExpect(status().isOk)
        assertEquals(7L, requireNotNull(lastUpdate).first)

        mockMvc
            .perform(
                put("/v1/groups/g7")
                    .requestAttr(UserIdArgumentResolver.USER_ID_ATTRIBUTE, 1L)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body()),
            ).andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("GROUP_NOT_FOUND"))
    }

    @Test
    fun `편집에서 imageAssetId 생략은 유지, null은 삭제로 구분된다 (TMT-415)`() {
        mockMvc
            .perform(
                put("/v1/groups/group_7")
                    .requestAttr(UserIdArgumentResolver.USER_ID_ATTRIBUTE, 1L)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(bodyWithoutImage()),
            ).andExpect(status().isOk)
        assertEquals(ImageAssetSelection.Keep, requireNotNull(lastUpdate).second.imageAsset)

        mockMvc
            .perform(
                put("/v1/groups/group_7")
                    .requestAttr(UserIdArgumentResolver.USER_ID_ATTRIBUTE, 1L)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body()),
            ).andExpect(status().isOk)
        assertEquals(ImageAssetSelection.None, requireNotNull(lastUpdate).second.imageAsset)
    }

    @Test
    fun `비로그인 생성은 401이다`() {
        mockMvc
            .perform(post("/v1/groups").contentType(MediaType.APPLICATION_JSON).content(body()))
            .andExpect(status().isUnauthorized)
    }

    private fun body(imageAssetId: String = "null") =
        """
        {"name":"새 그룹","oneLineDescription":"한줄","foodCategoryId":"cat_korean",
         "regionTagIds":["region_guro"],"imageAssetId":$imageAssetId}
        """.trimIndent()

    private fun bodyWithoutImage() =
        """
        {"name":"새 그룹","oneLineDescription":"한줄","foodCategoryId":"cat_korean",
         "regionTagIds":["region_guro"]}
        """.trimIndent()

    private fun view() =
        GroupDetailView(
            groupId = 10L,
            name = "새 그룹",
            oneLineDescription = "한줄",
            description = null,
            imageUrl = null,
            coverImages = listOf(GroupDetailView.CoverImage("https://m/x.jpg", 3L)),
            memberCount = 1,
            reviewCount = 0,
            placeCount = 0,
            foodCategoryId = "cat_korean",
            regionTagIds = listOf("region_guro"),
            matchedSavedPlaceCount = 0,
            isMember = true,
            isOwner = true,
        )
}
