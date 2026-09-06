package com.tmt.input.http.controller.address

import com.tmt.application.port.input.AddressSearchRequest
import com.tmt.application.port.input.AddressSearchResult
import com.tmt.application.port.input.SearchAddressesUseCase
import com.tmt.application.port.output.address.AddressCandidate
import com.tmt.input.http.auth.UserIdArgumentResolver
import com.tmt.input.http.exception.ExceptionAdvice
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import kotlin.test.assertEquals

class AddressSearchControllerTest {
    private val useCase = StubSearchAddressesUseCase()

    private val mockMvc: MockMvc =
        MockMvcBuilders
            .standaloneSetup(AddressSearchController(useCase, AddressIdTokenCodec("test-secret")))
            .setCustomArgumentResolvers(UserIdArgumentResolver())
            .setControllerAdvice(ExceptionAdvice())
            .build()

    @Test
    fun `인증이 없으면 401이다`() {
        mockMvc
            .perform(get("/v1/addresses/search").param("query", "오목로32길"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `응답에 좌표가 없고 regionName과 truncated가 있다`() {
        mockMvc
            .perform(search("오목로32길"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items[0].latitude").doesNotExist())
            .andExpect(jsonPath("$.items[0].longitude").doesNotExist())
            .andExpect(jsonPath("$.items[0].regionName").value("양천구 신정동"))
            .andExpect(jsonPath("$.items[0].addressId").isNotEmpty)
            .andExpect(jsonPath("$.truncated").value(false))
    }

    @Test
    fun `정제 후 2자 미만이면 VALIDATION_FAILED이고 유스케이스를 부르지 않는다`() {
        mockMvc
            .perform(search("OR"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))

        assertEquals(0, useCase.calls.size)
    }

    @Test
    fun `limit은 기본 20이고 상한 50으로 절삭된다`() {
        mockMvc.perform(search("오목로32길")).andExpect(status().isOk)
        assertEquals(20, useCase.calls.last().limit)

        mockMvc.perform(search("오목로32길").param("limit", "500")).andExpect(status().isOk)
        assertEquals(50, useCase.calls.last().limit)
    }

    @Test
    fun `다음 커서로 다음 페이지를 요청한다`() {
        useCase.nextPage = 2
        val cursor =
            mockMvc
                .perform(search("오목로32길"))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andReturn()
                .response.contentAsString
                .substringAfter("\"nextCursor\":\"")
                .substringBefore('"')

        mockMvc.perform(search("오목로32길").param("cursor", cursor)).andExpect(status().isOk)

        assertEquals(2, useCase.calls.last().page)
    }

    @Test
    fun `검색어가 바뀌면 이전 커서는 INVALID_CURSOR다`() {
        useCase.nextPage = 2
        val cursor =
            mockMvc
                .perform(search("오목로32길"))
                .andReturn()
                .response.contentAsString
                .substringAfter("\"nextCursor\":\"")
                .substringBefore('"')

        mockMvc
            .perform(search("도화동").param("cursor", cursor))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_CURSOR"))
    }

    @Test
    fun `정제 결과가 같은 두 검색어는 같은 커서를 쓴다`() {
        useCase.nextPage = 2
        val cursor =
            mockMvc
                .perform(search("오목로32길"))
                .andReturn()
                .response.contentAsString
                .substringAfter("\"nextCursor\":\"")
                .substringBefore('"')

        // 조건 해시는 정제된 검색어로 만든다 — 원문으로 만들면 여기서 INVALID_CURSOR가 난다
        mockMvc
            .perform(search("오목로32길'").param("cursor", cursor))
            .andExpect(status().isOk)
    }

    @Test
    fun `해석할 수 없는 커서는 INVALID_CURSOR다`() {
        mockMvc
            .perform(search("오목로32길").param("cursor", "not-a-cursor"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_CURSOR"))
    }

    private fun search(query: String) =
        get("/v1/addresses/search").requestAttr(UserIdArgumentResolver.USER_ID_ATTRIBUTE, 1L).param("query", query)

    private class StubSearchAddressesUseCase : SearchAddressesUseCase {
        val calls = mutableListOf<AddressSearchRequest>()
        var nextPage: Int? = null

        override fun search(request: AddressSearchRequest): AddressSearchResult {
            calls += request
            return AddressSearchResult(
                items =
                    listOf(
                        AddressCandidate(
                            admCd = "1147010100",
                            rnMgtSn = "114704166011",
                            udrtYn = "0",
                            buldMnnm = "1",
                            buldSlno = "0",
                            roadAddress = "서울특별시 양천구 오목로32길 1",
                            jibunAddress = "서울특별시 양천구 신정동 948-1",
                            regionName = "양천구 신정동",
                        ),
                    ),
                sanitizedQuery = request.query.orEmpty(),
                hasNext = nextPage != null,
                truncated = false,
                nextPage = nextPage,
            )
        }
    }
}
