package com.tmt.input.http.controller

import com.tmt.application.domain.idempotency.IdempotencyPayloadCodec
import com.tmt.application.domain.idempotency.IdempotencyRaceLostException
import com.tmt.application.domain.idempotency.IdempotencyRecord
import com.tmt.application.domain.idempotency.IdempotencyService
import com.tmt.application.domain.idempotency.IdempotentRequestTransaction
import com.tmt.application.port.input.CreateSaveCommand
import com.tmt.application.port.input.CreateSaveUseCase
import com.tmt.application.port.input.SaveResult
import com.tmt.application.port.output.persistence.IdempotencyPort
import com.tmt.input.http.auth.UserIdArgumentResolver
import com.tmt.input.http.exception.ExceptionAdvice
import com.tmt.input.http.idempotency.IdempotencyKeyArgumentResolver
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Instant

class SaveControllerTest {
    private val createSaveUseCase = RecordingCreateSaveUseCase()
    private val codec = IdempotencyPayloadCodec()
    private val idempotencyPort = InMemoryIdempotencyPort()

    private val mockMvc: MockMvc =
        MockMvcBuilders
            .standaloneSetup(
                SaveController(
                    createSaveUseCase = createSaveUseCase,
                    idempotentRequestUseCase =
                        IdempotencyService(
                            idempotencyPort,
                            codec,
                            IdempotentRequestTransaction(idempotencyPort, codec),
                        ),
                ),
            ).setCustomArgumentResolvers(UserIdArgumentResolver(), IdempotencyKeyArgumentResolver())
            .setControllerAdvice(ExceptionAdvice())
            .build()

    private fun postSave(
        body: String,
        userId: Long = 1,
        idempotencyKey: String? = "key-1",
    ) = mockMvc.perform(
        post("/v1/saves")
            .header(UserIdArgumentResolver.HEADER, userId.toString())
            .apply { idempotencyKey?.let { header(IdempotencyKeyArgumentResolver.HEADER, it) } }
            .contentType(MediaType.APPLICATION_JSON)
            .content(body),
    )

    @Test
    fun `작성 완료는 201과 Location을 준다`() {
        postSave("""{ "placeId": "place_1", "rating": 5 }""")
            .andExpect(status().isCreated)
            .andExpect(header().string("Location", "/v1/saves/save_1"))
            .andExpect(jsonPath("$.saveId").value("save_1"))
            .andExpect(jsonPath("$.placeId").value("place_1"))
            .andExpect(jsonPath("$.reviewId").doesNotExist())
            .andExpect(jsonPath("$.ticket.grantedCount").value(0))
            .andExpect(jsonPath("$.ticket.availableCount").value(1))

        assertEquals(5, createSaveUseCase.commands.single().rating)
    }

    @Test
    fun `리뷰가 되면 reviewId와 발급 티켓이 실린다 (S3)`() {
        createSaveUseCase.reviewed = true

        postSave("""{ "placeId": "place_1", "photoAssetIds": ["7"], "rating": 5, "content": "맛있어요" }""")
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.reviewId").value("rv_100"))
            .andExpect(jsonPath("$.ticket.grantedCount").value(1))

        assertEquals(listOf(7L), createSaveUseCase.commands.single().photoAssetIds)
    }

    @Test
    fun `같은 키 재요청에 Save가 두 번 생기지 않는다 (규약 §9)`() {
        val body = """{ "placeId": "place_1" }"""
        postSave(body).andExpect(status().isCreated).andExpect(jsonPath("$.saveId").value("save_1"))

        // 최초 응답을 그대로 재현한다 — 저장이 하나 더 생기지 않는다
        postSave(body).andExpect(status().isCreated).andExpect(jsonPath("$.saveId").value("save_1"))

        assertEquals(1, createSaveUseCase.commands.size)
    }

    @Test
    fun `같은 키에 다른 바디면 IDEMPOTENCY_CONFLICT다`() {
        postSave("""{ "placeId": "place_1" }""").andExpect(status().isCreated)

        postSave("""{ "placeId": "place_1", "rating": 3 }""")
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"))
    }

    @Test
    fun `Idempotency-Key 없이는 작성할 수 없다`() {
        postSave("""{ "placeId": "place_1" }""", idempotencyKey = null)
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
    }

    @Test
    fun `placeId가 없으면 VALIDATION_FAILED다 (C1)`() {
        postSave("""{ "rating": 5 }""")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
    }

    @Test
    fun `매장 직접 등록은 아직 받지 않는다 (TMT-193)`() {
        postSave("""{ "newPlace": { "name": "한판승부", "addressId": "token" } }""")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
    }

    @Test
    fun `접두가 어긋난 placeId는 없는 매장과 같다`() {
        postSave("""{ "placeId": "쓰레기" }""")
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("PLACE_NOT_FOUND"))
    }

    @Test
    fun `숫자가 아닌 assetId는 MEDIA_NOT_OWNED다 (M2)`() {
        postSave("""{ "placeId": "place_1", "photoAssetIds": ["asset_1"] }""")
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("MEDIA_NOT_OWNED"))
    }

    private class RecordingCreateSaveUseCase : CreateSaveUseCase {
        val commands = mutableListOf<CreateSaveCommand>()
        var reviewed = false

        override fun create(command: CreateSaveCommand): SaveResult {
            commands += command
            return SaveResult(
                saveId = commands.size.toLong(),
                reviewId = if (reviewed) 100L else null,
                placeId = command.placeId,
                grantedCount = if (reviewed) 1 else 0,
                availableCount = if (reviewed) 2 else 1,
            )
        }
    }

    /** 실제 어댑터와 같이 INSERT가 선점을 판정한다. */
    private class InMemoryIdempotencyPort : IdempotencyPort {
        private val records = mutableMapOf<Triple<Long, String, String>, IdempotencyRecord>()

        override fun find(
            userId: Long,
            endpoint: String,
            idemKey: String,
        ): IdempotencyRecord? = records[Triple(userId, endpoint, idemKey)]

        override fun insert(record: IdempotencyRecord) {
            val key = Triple(record.userId, record.endpoint, record.idemKey)
            if (records.putIfAbsent(key, record) != null) {
                throw IdempotencyRaceLostException(record.endpoint, record.idemKey)
            }
        }

        override fun deleteCreatedBefore(threshold: Instant): Int = 0
    }
}
