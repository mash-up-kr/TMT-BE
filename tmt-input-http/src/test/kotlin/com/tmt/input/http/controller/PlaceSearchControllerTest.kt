package com.tmt.input.http.controller

import com.tmt.application.port.input.PlaceCardView
import com.tmt.application.port.input.PlaceSearchKey
import com.tmt.application.port.input.PlaceSearchRequest
import com.tmt.application.port.input.PlaceSearchResult
import com.tmt.application.port.input.SearchPlacesUseCase
import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException
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
 * 매장 검색 실구현의 어댑터 계약 (TMT-195) — 응답 형태·ID 표기·커서 왕복·필수 파라미터가 mock과 같은지 지킨다.
 */
class PlaceSearchControllerTest {
    private var lastRequest: PlaceSearchRequest? = null

    /** 정렬 키가 있는 페이지 소스. 유스케이스 대신 세워 커서 왕복만 본다. */
    private var pages: List<PlaceSearchResult> = listOf(emptyResult())
    private var pageIndex = 0

    private val useCase =
        object : SearchPlacesUseCase {
            override fun search(request: PlaceSearchRequest): PlaceSearchResult {
                lastRequest = request
                // 서비스가 하는 필수 검증을 같은 규칙으로 흉내낸다 — 어댑터가 400을 그대로 흘리는지 본다
                if (request.query.isNullOrBlank() && request.curationTagId == null) {
                    throw TmtException(ErrorCode.VALIDATION_FAILED, "query와 curationTagId 중 최소 하나는 있어야 합니다.")
                }
                val index = if (request.after == null) 0 else pageIndex
                return pages[index]
            }
        }

    private val mockMvc: MockMvc =
        MockMvcBuilders
            .standaloneSetup(PlaceSearchController(useCase))
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
    fun `PlaceCard가 mock과 같은 접두 ID 표기로 나간다`() {
        pages = listOf(PlaceSearchResult(items = listOf(placeCard(1)), hasNext = false, lastKey = null))

        mockMvc
            .perform(get("/v1/places/search").param("query", "델리스"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items[0].placeId").value("place_1"))
            .andExpect(jsonPath("$.items[0].name").value("델리스피자"))
            .andExpect(jsonPath("$.items[0].regionName").value("마포구 도화동"))
            .andExpect(jsonPath("$.items[0].categoryName").value("양식"))
            .andExpect(jsonPath("$.items[0].reviewCount").value(0))
            .andExpect(jsonPath("$.items[0].averageRating").doesNotExist())
            .andExpect(jsonPath("$.items[0].distanceMeters").doesNotExist())
            .andExpect(jsonPath("$.items[0].isFavorite").value(false))
            .andExpect(jsonPath("$.hasNext").value(false))
            .andExpect(jsonPath("$.nextCursor").doesNotExist())
    }

    @Test
    fun `결과 0건은 오류가 아니라 빈 배열이다`() {
        mockMvc
            .perform(get("/v1/places/search").param("query", "없는가게"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(0))
            .andExpect(jsonPath("$.hasNext").value(false))
    }

    @Test
    fun `nearbyOnly 기본값은 false이고 좌표는 그대로 전달된다`() {
        mockMvc
            .perform(
                get("/v1/places/search").param("query", "델리스").param("latitude", "37.5").param("longitude", "127.0"),
            ).andExpect(status().isOk)

        assertEquals(false, lastRequest?.nearbyOnly)
        assertEquals(37.5, lastRequest?.latitude)
        assertEquals(127.0, lastRequest?.longitude)
        assertEquals(20, lastRequest?.limit)
    }

    @Test
    fun `limit은 51 이상이면 50으로 잘린다`() {
        mockMvc
            .perform(get("/v1/places/search").param("query", "델리스").param("limit", "51"))
            .andExpect(status().isOk)

        assertEquals(50, lastRequest?.limit)
    }

    @Test
    fun `커서 왕복에 중복도 누락도 없다`() {
        // 같은 유사도 점수(700)를 가진 3건이 경계에 걸린다 — tie-breaker가 placeId다
        pages =
            listOf(
                PlaceSearchResult(
                    items = listOf(placeCard(3), placeCard(2)),
                    hasNext = true,
                    lastKey = PlaceSearchKey(sortValue = 700, placeId = 2),
                ),
                PlaceSearchResult(items = listOf(placeCard(1)), hasNext = false, lastKey = null),
            )
        pageIndex = 1

        val first =
            mockMvc
                .perform(get("/v1/places/search").param("query", "델리스"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.items[0].placeId").value("place_3"))
                .andExpect(jsonPath("$.items[1].placeId").value("place_2"))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andReturn()
        val nextCursor = cursorOf(first.response.contentAsString)
        assertNotNull(nextCursor)

        mockMvc
            .perform(get("/v1/places/search").param("query", "델리스").param("cursor", nextCursor))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].placeId").value("place_1"))
            .andExpect(jsonPath("$.hasNext").value(false))

        assertEquals(PlaceSearchKey(700, 2), lastRequest?.after)
    }

    @Test
    fun `조건이 바뀐 커서는 INVALID_CURSOR다`() {
        pages =
            listOf(
                PlaceSearchResult(
                    items = listOf(placeCard(3)),
                    hasNext = true,
                    lastKey = PlaceSearchKey(sortValue = 700, placeId = 3),
                ),
            )

        val first =
            mockMvc
                .perform(get("/v1/places/search").param("query", "델리스"))
                .andExpect(status().isOk)
                .andReturn()
        val nextCursor = cursorOf(first.response.contentAsString)

        mockMvc
            .perform(get("/v1/places/search").param("query", "서북").param("cursor", nextCursor))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_CURSOR"))

        // 좌표가 붙으면 정렬 축이 거리로 바뀐다 — 이전 커서를 그대로 쓰면 안 된다
        mockMvc
            .perform(
                get("/v1/places/search")
                    .param("query", "델리스")
                    .param("latitude", "37.5")
                    .param("longitude", "127.0")
                    .param("cursor", nextCursor),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_CURSOR"))
    }

    @Test
    fun `해석할 수 없는 커서는 INVALID_CURSOR다`() {
        mockMvc
            .perform(get("/v1/places/search").param("query", "델리스").param("cursor", "not-a-cursor"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_CURSOR"))
    }

    private fun cursorOf(body: String): String {
        val marker = "\"nextCursor\":\""
        val start = body.indexOf(marker) + marker.length
        return body.substring(start, body.indexOf('"', start))
    }

    private fun placeCard(id: Long) =
        PlaceCardView(
            placeId = id,
            name = "델리스피자",
            roadAddress = "서울 마포구 도화동 200-14",
            regionName = "마포구 도화동",
            categoryName = "양식",
            averageRating = null,
            reviewCount = 0,
            thumbnailUrl = null,
            distanceMeters = null,
            isFavorite = false,
        )

    private companion object {
        fun emptyResult() = PlaceSearchResult(items = emptyList(), hasNext = false, lastKey = null)
    }
}
