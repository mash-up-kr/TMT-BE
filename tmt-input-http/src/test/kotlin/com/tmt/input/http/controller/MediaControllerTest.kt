package com.tmt.input.http.controller

import com.tmt.application.domain.media.MediaRules
import com.tmt.application.port.input.CreateUploadIntentUseCase
import com.tmt.application.port.input.UploadIntent
import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException
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
import java.time.Instant

/**
 * 발급 계약(assetId·uploadUrl·expiresAt)이 mock과 같은지 지킨다 — 형태가 같아야
 * FE orval 재생성이 필요 없다 (TMT-202 승인 기준).
 */
class MediaControllerTest {
    private var lastOwnerId: Long? = null

    private val useCase =
        object : CreateUploadIntentUseCase {
            override fun create(
                ownerId: Long,
                contentType: String,
                contentLength: Long,
            ): UploadIntent {
                // 서비스(MediaUploadService)와 같은 순서로 거른다 — 형식 먼저, 크기 다음
                if (contentType !in MediaRules.ALLOWED_CONTENT_TYPES) {
                    throw TmtException(ErrorCode.MEDIA_CONTENT_TYPE_NOT_ALLOWED)
                }
                if (contentLength <= 0 || contentLength > MediaRules.MAX_CONTENT_LENGTH) {
                    throw TmtException(ErrorCode.MEDIA_FILE_TOO_LARGE)
                }
                lastOwnerId = ownerId
                return UploadIntent(
                    assetId = 1,
                    uploadUrl = "https://media.test.tmt/review/uuid.jpg?X-Amz-Signature=x",
                    expiresAt = Instant.parse("2026-08-28T00:10:00Z"),
                )
            }
        }

    private val mockMvc: MockMvc =
        MockMvcBuilders
            .standaloneSetup(MediaController(useCase))
            .setCustomArgumentResolvers(UserIdArgumentResolver())
            .setControllerAdvice(ExceptionAdvice())
            .build()

    private fun request(body: String) =
        post("/v1/media/upload-intents")
            .header(UserIdArgumentResolver.HEADER, "1")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body)

    @Test
    fun `presigned URL을 발급하고 요청자를 소유자로 넘긴다 (M2)`() {
        mockMvc
            .perform(request("""{ "contentType": "image/jpeg", "contentLength": 1048576 }"""))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.assetId").value("1"))
            .andExpect(jsonPath("$.uploadUrl").isNotEmpty)
            .andExpect(jsonPath("$.expiresAt").isNotEmpty)

        assertEquals(1L, lastOwnerId)
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
