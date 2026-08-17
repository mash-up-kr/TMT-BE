package com.tmt.input.http.mock

import com.tmt.input.http.auth.UserIdArgumentResolver
import com.tmt.input.http.exception.ExceptionAdvice
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class PlaceMockControllerTest {
    private val placeStore =
        InMemoryStore<MockPlace>(idPrefix = "place").apply {
            create { id -> MockPlace(id, "델리스피자", "서울 마포구 도화동 200-14", "마포구 도화동", "양식", 37.5399, 126.9515) }
            create { id -> MockPlace(id, "서북면옥", "서울 중구 을지로3가 296-1", "중구 을지로3가", "한식", 37.5663, 126.9910) }
            create { id -> MockPlace(id, "역전할머니맥주", "서울 강남구 역삼동 815-3", "강남구 역삼동", "주점", 37.4993, 127.0275) }
        }
    private val addressStore = InMemoryStore<MockAddress>(idPrefix = "addr")
    private val saveStore = InMemoryStore<MockSave>(idPrefix = "save")

    private val mockMvc: MockMvc =
        MockMvcBuilders
            .standaloneSetup(PlaceMockController(placeStore, addressStore, saveStore, MockFavoriteStore()))
            .setCustomArgumentResolvers(UserIdArgumentResolver())
            .setControllerAdvice(ExceptionAdvice())
            .build()

    @Test
    fun `query와 curationTagId 둘 다 없으면 VALIDATION_FAILED다`() {
        mockMvc
            .perform(get("/v1/places/search"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
    }

    @Test
    fun `매장명으로 검색하면 PlaceCard 목록이 내려간다`() {
        mockMvc
            .perform(get("/v1/places/search").param("query", "델리스"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].placeId").value("place_1"))
            .andExpect(jsonPath("$.items[0].name").value("델리스피자"))
            .andExpect(jsonPath("$.items[0].regionName").value("마포구 도화동"))
            .andExpect(jsonPath("$.items[0].reviewCount").value(0))
            .andExpect(jsonPath("$.items[0].averageRating").doesNotExist())
            .andExpect(jsonPath("$.items[0].distanceMeters").doesNotExist())
            .andExpect(jsonPath("$.items[0].isFavorite").value(false))
            .andExpect(jsonPath("$.hasNext").value(false))
    }

    @Test
    fun `결과 0건은 오류가 아니라 빈 배열이다`() {
        mockMvc
            .perform(get("/v1/places/search").param("query", "없는가게"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items").isEmpty)
            .andExpect(jsonPath("$.hasNext").value(false))
    }

    @Test
    fun `좌표를 주면 distanceMeters가 채워진다`() {
        mockMvc
            .perform(
                get("/v1/places/search")
                    .param("query", "델리스")
                    .param("latitude", "37.5399")
                    .param("longitude", "126.9515"),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.items[0].distanceMeters").value(0))
    }

    @Test
    fun `큐레이션 칩은 검색 조건 프리셋으로 동작한다`() {
        mockMvc
            .perform(get("/v1/places/search").param("curationTagId", "curation_ganmaek"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].name").value("역전할머니맥주"))
    }

    @Test
    fun `매장을 직접 등록하면 201과 Location이 내려간다`() {
        val address =
            addressStore.create { id ->
                MockAddress(id, "서울 양천구 오목로 32길 1", "서울 양천구 신정동 948-1", 37.5261, 126.8558)
            }

        mockMvc
            .perform(
                post("/v1/places")
                    .header(UserIdArgumentResolver.HEADER, "1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """{ "name": "마피아 피자", "addressId": "${address.addressId}", "categoryId": "cat_western" }""",
                    ),
            ).andExpect(status().isCreated)
            .andExpect(header().string("Location", "/v1/places/place_4"))
            .andExpect(jsonPath("$.placeId").value("place_4"))
            .andExpect(jsonPath("$.name").value("마피아 피자"))
            .andExpect(jsonPath("$.regionName").value("양천구 신정동"))
            .andExpect(jsonPath("$.categoryName").value("양식"))
    }

    @Test
    fun `같은 좌표·같은 매장명이면 새로 만들지 않고 200으로 기존 매장을 돌려준다`() {
        val address =
            addressStore.create { id ->
                MockAddress(id, "서울 양천구 오목로 32길 1", "서울 양천구 신정동 948-1", 37.5261, 126.8558)
            }
        val body = """{ "name": "마피아 피자", "addressId": "${address.addressId}" }"""
        val create =
            post(
                "/v1/places",
            ).header(UserIdArgumentResolver.HEADER, "1").contentType(MediaType.APPLICATION_JSON).content(body)

        mockMvc.perform(create).andExpect(status().isCreated)
        mockMvc
            .perform(create)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.placeId").value("place_4"))
    }

    @Test
    fun `addressId가 없으면 ADDRESS_NOT_FOUND다`() {
        mockMvc
            .perform(
                post("/v1/places")
                    .header(UserIdArgumentResolver.HEADER, "1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{ "name": "마피아 피자", "addressId": "addr_999" }"""),
            ).andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("ADDRESS_NOT_FOUND"))
    }

    @Test
    fun `카테고리가 목록에 없으면 PLACE_CATEGORY_NOT_FOUND다`() {
        val address =
            addressStore.create { id ->
                MockAddress(id, "서울 양천구 오목로 32길 1", "서울 양천구 신정동 948-1", 37.5261, 126.8558)
            }

        mockMvc
            .perform(
                post("/v1/places")
                    .header(UserIdArgumentResolver.HEADER, "1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """{ "name": "마피아 피자", "addressId": "${address.addressId}", "categoryId": "cat_unknown" }""",
                    ),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("PLACE_CATEGORY_NOT_FOUND"))
    }
}
