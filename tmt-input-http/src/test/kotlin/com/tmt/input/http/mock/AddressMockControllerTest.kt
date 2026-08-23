package com.tmt.input.http.mock

import com.tmt.input.http.auth.UserIdArgumentResolver
import com.tmt.input.http.exception.ExceptionAdvice
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class AddressMockControllerTest {
    private val mockMvc: MockMvc =
        MockMvcBuilders
            .standaloneSetup(AddressMockController())
            .setCustomArgumentResolvers(UserIdArgumentResolver())
            .setControllerAdvice(ExceptionAdvice())
            .build()

    private fun search(query: String?) =
        get("/v1/addresses/search")
            .header(UserIdArgumentResolver.HEADER, "1")
            .apply { query?.let { param("query", it) } }

    @Test
    fun `query 없이 검색하면 VALIDATION_FAILED다`() {
        mockMvc
            .perform(search(null))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
    }

    @Test
    fun `정제 후 2자 미만이면 juso를 부르지 않고 VALIDATION_FAILED다`() {
        mockMvc
            .perform(search("a="))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
    }

    @Test
    fun `주소 검색 결과는 도로명·지번·지역명을 갖고 좌표를 내리지 않는다`() {
        mockMvc
            .perform(search("오목로"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items[0].addressId").isNotEmpty)
            .andExpect(jsonPath("$.items[0].roadAddress").isNotEmpty)
            .andExpect(jsonPath("$.items[0].jibunAddress").isNotEmpty)
            .andExpect(jsonPath("$.items[0].regionName").isNotEmpty)
            .andExpect(jsonPath("$.items[0].latitude").doesNotExist())
            .andExpect(jsonPath("$.items[0].longitude").doesNotExist())
            .andExpect(jsonPath("$.truncated").value(false))
    }

    @Test
    fun `같은 검색어는 같은 addressId를 돌려준다`() {
        val first =
            mockMvc
                .perform(search("오목로"))
                .andReturn()
                .response.contentAsString
        val second =
            mockMvc
                .perform(search("오목로"))
                .andReturn()
                .response.contentAsString

        assertEquals(first, second)
    }

    @Test
    fun `addressId는 불투명 토큰이라 주소를 복원할 수 있다`() {
        val body =
            mockMvc
                .perform(search("오목로"))
                .andReturn()
                .response.contentAsString
        val token = Regex("\"addressId\":\"([^\"]+)\"").find(body)!!.groupValues[1]

        val decoded = MockAddressToken.decode(token)

        assertEquals("양천구 신정동", decoded.regionName)
        assertEquals(true, decoded.hasCoordinate)
    }

    @Test
    fun `조작된 addressId는 VALIDATION_FAILED다`() {
        val body =
            mockMvc
                .perform(search("오목로"))
                .andReturn()
                .response.contentAsString
        val token = Regex("\"addressId\":\"([^\"]+)\"").find(body)!!.groupValues[1]

        val tampered = token.substringBeforeLast('.') + ".deadbeef"

        assertEquals(
            "VALIDATION_FAILED",
            runCatching { MockAddressToken.decode(tampered) }
                .exceptionOrNull()
                .let { (it as com.tmt.common.exception.TmtException).errorCode.name },
        )
    }

    @Test
    fun `공급자 장애는 502 ADDRESS_PROVIDER_UNAVAILABLE이다`() {
        mockMvc
            .perform(search("장애 테스트"))
            .andExpect(status().isBadGateway)
            .andExpect(jsonPath("$.code").value("ADDRESS_PROVIDER_UNAVAILABLE"))
    }

    @Test
    fun `페이지 상한에 걸리면 truncated가 true다`() {
        mockMvc
            .perform(search("많음 주소"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.truncated").value(true))
    }

    @Test
    fun `검색어 정제는 특수문자를 지우고 SQL 예약어 토큰만 걸러낸다`() {
        assertEquals("오목로 32길", AddressMockController.sanitize("오목로 32길"))
        // 특수문자 제거 — juso가 SQL Injection으로 보고 IP를 차단한다
        assertEquals("오목로32길", AddressMockController.sanitize("오목로'=32길%"))
        // 예약어는 토큰 단위로만 걸린다
        assertEquals("서울 타워", AddressMockController.sanitize("서울 OR 타워"))
        assertEquals("ORIGIN UNIONMALL", AddressMockController.sanitize("ORIGIN UNIONMALL"))
        // 지번의 하이픈은 남긴다
        assertEquals("신정동 948-1", AddressMockController.sanitize("신정동 948-1"))
    }
}
