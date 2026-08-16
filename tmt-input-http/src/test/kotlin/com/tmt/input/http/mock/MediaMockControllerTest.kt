package com.tmt.input.http.mock

import com.tmt.input.http.auth.UserIdArgumentResolver
import com.tmt.input.http.exception.ExceptionAdvice
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class MediaMockControllerTest {
    private val assetStore = InMemoryStore<MockAsset>(idPrefix = "asset")

    private val mockMvc: MockMvc =
        MockMvcBuilders
            .standaloneSetup(MediaMockController(assetStore))
            .setCustomArgumentResolvers(UserIdArgumentResolver())
            .setControllerAdvice(ExceptionAdvice())
            .build()

    private fun request(body: String) =
        post("/v1/media/upload-intents")
            .header(UserIdArgumentResolver.HEADER, "1")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body)

    @Test
    fun `presigned URL을 발급하고 asset을 요청자 소유로 기록한다`() {
        mockMvc
            .perform(request("""{ "contentType": "image/jpeg", "contentLength": 1048576 }"""))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.assetId").value("asset_1"))
            .andExpect(jsonPath("$.uploadUrl").isNotEmpty)
            .andExpect(jsonPath("$.expiresAt").isNotEmpty)

        assertEquals(1L, assetStore.findById("asset_1")?.ownerId)
    }

    @Test
    fun `5MB를 넘으면 MEDIA_FILE_TOO_LARGE다`() {
        mockMvc
            .perform(request("""{ "contentType": "image/jpeg", "contentLength": 5242881 }"""))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("MEDIA_FILE_TOO_LARGE"))
    }

    @Test
    fun `허용 목록 밖 형식이면 MEDIA_CONTENT_TYPE_NOT_ALLOWED다`() {
        mockMvc
            .perform(request("""{ "contentType": "image/heic", "contentLength": 1024 }"""))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("MEDIA_CONTENT_TYPE_NOT_ALLOWED"))
    }

    @Test
    fun `인증 없이는 발급할 수 없다`() {
        mockMvc
            .perform(
                post("/v1/media/upload-intents")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{ "contentType": "image/jpeg", "contentLength": 1024 }"""),
            ).andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
    }
}
