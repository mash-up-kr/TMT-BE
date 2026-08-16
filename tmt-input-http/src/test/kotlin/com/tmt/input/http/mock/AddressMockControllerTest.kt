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
    private val addressStore = InMemoryStore<MockAddress>(idPrefix = "addr")

    private val mockMvc: MockMvc =
        MockMvcBuilders
            .standaloneSetup(AddressMockController(addressStore))
            .setCustomArgumentResolvers(UserIdArgumentResolver())
            .setControllerAdvice(ExceptionAdvice())
            .build()

    @Test
    fun `query 없이 검색하면 VALIDATION_FAILED다`() {
        mockMvc
            .perform(get("/v1/addresses/search").header(UserIdArgumentResolver.HEADER, "1"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
    }

    @Test
    fun `주소 검색 결과는 도로명·지번·좌표를 갖는다`() {
        mockMvc
            .perform(get("/v1/addresses/search").header(UserIdArgumentResolver.HEADER, "1").param("query", "오목로"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(3))
            .andExpect(jsonPath("$.items[0].addressId").value("addr_1"))
            .andExpect(jsonPath("$.items[0].roadAddress").isNotEmpty)
            .andExpect(jsonPath("$.items[0].jibunAddress").isNotEmpty)
            .andExpect(jsonPath("$.items[0].latitude").isNumber)
            .andExpect(jsonPath("$.items[0].longitude").isNumber)
    }

    @Test
    fun `같은 검색어는 같은 addressId를 돌려준다`() {
        val search = get("/v1/addresses/search").header(UserIdArgumentResolver.HEADER, "1").param("query", "오목로")

        mockMvc.perform(search).andExpect(jsonPath("$.items[0].addressId").value("addr_1"))
        mockMvc.perform(search).andExpect(jsonPath("$.items[0].addressId").value("addr_1"))

        assertEquals(3, addressStore.findAll().size)
    }
}
