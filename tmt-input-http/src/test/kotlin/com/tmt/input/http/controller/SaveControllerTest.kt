package com.tmt.input.http.controller

import com.tmt.application.domain.idempotency.IdempotencyPayloadCodec
import com.tmt.application.domain.idempotency.IdempotencyRaceLostException
import com.tmt.application.domain.idempotency.IdempotencyRecord
import com.tmt.application.domain.idempotency.IdempotencyService
import com.tmt.application.domain.idempotency.IdempotentRequestTransaction
import com.tmt.application.port.input.CreateSaveCommand
import com.tmt.application.port.input.CreateSaveUseCase
import com.tmt.application.port.input.DeleteSaveUseCase
import com.tmt.application.port.input.GetSaveUseCase
import com.tmt.application.port.input.ListMySavesUseCase
import com.tmt.application.port.input.MySaveView
import com.tmt.application.port.input.MySavesRequest
import com.tmt.application.port.input.MySavesResult
import com.tmt.application.port.input.PlaceSelection
import com.tmt.application.port.input.ResolveAddressCoordinateUseCase
import com.tmt.application.port.input.SaveDetailView
import com.tmt.application.port.input.SaveResult
import com.tmt.application.port.input.UpdateSaveCommand
import com.tmt.application.port.input.UpdateSaveUseCase
import com.tmt.application.port.output.address.AddressCandidate
import com.tmt.application.port.output.address.AddressCoordinateKey
import com.tmt.application.port.output.persistence.IdempotencyPort
import com.tmt.application.port.output.persistence.Wgs84Point
import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException
import com.tmt.input.http.auth.UserIdArgumentResolver
import com.tmt.input.http.controller.address.AddressIdTokenCodec
import com.tmt.input.http.exception.ExceptionAdvice
import com.tmt.input.http.idempotency.IdempotencyKeyArgumentResolver
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Instant

class SaveControllerTest {
    private val createSaveUseCase = RecordingCreateSaveUseCase()
    private val updateSaveUseCase = RecordingUpdateSaveUseCase()
    private val deleteSaveUseCase = RecordingDeleteSaveUseCase()
    private val getSaveUseCase = StubGetSaveUseCase()
    private val listMySavesUseCase = InMemoryListMySavesUseCase()
    private val codec = IdempotencyPayloadCodec()
    private val idempotencyPort = InMemoryIdempotencyPort()
    private val addressIdTokenCodec = AddressIdTokenCodec(secret = "test-secret")
    private val coordinateUseCase = FakeCoordinateUseCase()

    private val addressToken =
        addressIdTokenCodec.encode(
            AddressCandidate(
                admCd = "1147010100",
                rnMgtSn = "114703122009",
                udrtYn = "0",
                buldMnnm = "1",
                buldSlno = "0",
                roadAddress = "서울특별시 양천구 오목로32길 1",
                jibunAddress = "서울특별시 양천구 신정동 948-1",
                regionName = "양천구 신정동",
            ),
        )

    private val mockMvc: MockMvc =
        MockMvcBuilders
            .standaloneSetup(
                SaveController(
                    createSaveUseCase = createSaveUseCase,
                    updateSaveUseCase = updateSaveUseCase,
                    deleteSaveUseCase = deleteSaveUseCase,
                    getSaveUseCase = getSaveUseCase,
                    listMySavesUseCase = listMySavesUseCase,
                    idempotentRequestUseCase =
                        IdempotencyService(
                            idempotencyPort,
                            codec,
                            IdempotentRequestTransaction(idempotencyPort, codec),
                        ),
                    addressIdTokenCodec = addressIdTokenCodec,
                    resolveAddressCoordinateUseCase = coordinateUseCase,
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
    fun `매장 직접 등록은 좌표를 확보해 Place 생성 커맨드로 넘어간다 (P8)`() {
        postSave(
            """{ "newPlace": { "name": "  한판승부  ", "addressId": "$addressToken", "categoryId": "cat_western" } }""",
        ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.placeId").value("place_900"))

        val place = createSaveUseCase.commands.single().place as PlaceSelection.New
        assertEquals("한판승부", place.name)
        assertEquals("서울특별시 양천구 오목로32길 1", place.roadAddress)
        assertEquals("양천구 신정동", place.regionName)
        assertEquals(37.5209, place.latitude)
        assertEquals(1, coordinateUseCase.calls.size)
    }

    @Test
    fun `placeId와 newPlace를 함께 보내면 VALIDATION_FAILED다`() {
        postSave("""{ "placeId": "place_1", "newPlace": { "name": "한판승부", "addressId": "$addressToken" } }""")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))

        assertEquals(0, coordinateUseCase.calls.size)
    }

    @Test
    fun `서명이 어긋난 addressId는 VALIDATION_FAILED다`() {
        postSave("""{ "newPlace": { "name": "한판승부", "addressId": "$addressToken-tampered" } }""")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))

        assertEquals(0, coordinateUseCase.calls.size)
    }

    @Test
    fun `좌표를 못 얻으면 저장이 시작되지 않는다 (F §4-1)`() {
        coordinateUseCase.failure = TmtException(ErrorCode.ADDRESS_NOT_FOUND)

        postSave("""{ "newPlace": { "name": "한판승부", "addressId": "$addressToken" } }""")
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("ADDRESS_NOT_FOUND"))

        assertEquals(0, createSaveUseCase.commands.size)
    }

    @Test
    fun `좌표 공급자 장애는 502로 나가고 저장이 시작되지 않는다`() {
        coordinateUseCase.failure = TmtException(ErrorCode.ADDRESS_PROVIDER_UNAVAILABLE)

        postSave("""{ "newPlace": { "name": "한판승부", "addressId": "$addressToken" } }""")
            .andExpect(status().isBadGateway)
            .andExpect(jsonPath("$.code").value("ADDRESS_PROVIDER_UNAVAILABLE"))

        assertEquals(0, createSaveUseCase.commands.size)
    }

    @Test
    fun `같은 키 재요청에 Place가 두 번 생기지 않는다`() {
        val body = """{ "newPlace": { "name": "한판승부", "addressId": "$addressToken" } }"""
        postSave(body).andExpect(status().isCreated).andExpect(jsonPath("$.placeId").value("place_900"))
        postSave(body).andExpect(status().isCreated).andExpect(jsonPath("$.placeId").value("place_900"))

        // 재요청은 최초 응답을 재현한다 — 좌표 조회도 Place 생성도 다시 돌지 않는다
        assertEquals(1, createSaveUseCase.commands.size)
        assertEquals(1, coordinateUseCase.calls.size)
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

    private fun putSave(
        saveId: String,
        body: String,
        userId: Long = 1,
        idempotencyKey: String = "key-put",
    ) = mockMvc.perform(
        put("/v1/saves/$saveId")
            .header(UserIdArgumentResolver.HEADER, userId.toString())
            .header(IdempotencyKeyArgumentResolver.HEADER, idempotencyKey)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body),
    )

    @Test
    fun `이어쓰기는 200과 판정 결과를 준다`() {
        updateSaveUseCase.reviewed = true

        putSave("save_9", """{ "placeId": "place_1", "photoAssetIds": ["7"], "rating": 5, "content": "맛있어요" }""")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.saveId").value("save_9"))
            .andExpect(jsonPath("$.reviewId").value("rv_100"))
            .andExpect(jsonPath("$.ticket.grantedCount").value(1))

        val command = updateSaveUseCase.commands.single()
        assertEquals(9L, command.saveId)
        assertEquals(1L, command.placeId)
        assertEquals(listOf(7L), command.photoAssetIds)
    }

    @Test
    fun `이미 리뷰가 된 저장의 이어쓰기는 거부된다 (S4)`() {
        updateSaveUseCase.error = ErrorCode.SAVE_ALREADY_REVIEWED

        putSave("save_9", """{ "placeId": "place_1" }""")
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("SAVE_ALREADY_REVIEWED"))
    }

    @Test
    fun `매장 변경 시도는 SAVE_PLACE_IMMUTABLE이다 (S6)`() {
        updateSaveUseCase.error = ErrorCode.SAVE_PLACE_IMMUTABLE

        putSave("save_9", """{ "placeId": "place_2" }""")
            .andExpect(status().isUnprocessableEntity)
            .andExpect(jsonPath("$.code").value("SAVE_PLACE_IMMUTABLE"))
    }

    @Test
    fun `이어쓰기도 같은 키 재요청에 두 번 실행되지 않는다 (규약 §9)`() {
        val body = """{ "placeId": "place_1", "rating": 4 }"""
        putSave("save_9", body).andExpect(status().isOk)
        putSave("save_9", body).andExpect(status().isOk)

        assertEquals(1, updateSaveUseCase.commands.size)
    }

    @Test
    fun `경로 표기가 달라도 같은 저장이면 한 멱등 공간이다 (TMT-301)`() {
        val body = """{ "placeId": "place_1", "rating": 4 }"""

        // 멱등 키의 endpoint 성분이 경로 원문이면 save_9와 9가 다른 공간이 되어 두 번 실행된다
        putSave("save_9", body).andExpect(status().isOk)
        putSave("9", body).andExpect(status().isOk)

        assertEquals(1, updateSaveUseCase.commands.size)
    }

    @Test
    fun `임시저장 버리기는 204다`() {
        mockMvc
            .perform(delete("/v1/saves/save_9").header(UserIdArgumentResolver.HEADER, "1"))
            .andExpect(status().isNoContent)

        assertEquals(listOf(1L to 9L), deleteSaveUseCase.deleted)
    }

    @Test
    fun `리뷰가 된 저장은 이 경로로 지울 수 없다`() {
        deleteSaveUseCase.error = ErrorCode.SAVE_ALREADY_REVIEWED

        mockMvc
            .perform(delete("/v1/saves/save_9").header(UserIdArgumentResolver.HEADER, "1"))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("SAVE_ALREADY_REVIEWED"))
    }

    @Test
    fun `본인 상세는 mock과 같은 형태로 내려간다`() {
        mockMvc
            .perform(get("/v1/saves/save_9").header(UserIdArgumentResolver.HEADER, "1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.saveId").value("save_9"))
            .andExpect(jsonPath("$.place.placeId").value("place_1"))
            .andExpect(jsonPath("$.place.categoryName").value("한식"))
            .andExpect(jsonPath("$.photos[0].photoId").value("sp_5"))
            .andExpect(jsonPath("$.photos[0].url").value("https://media.tmt.example/photos/5.jpg"))
            .andExpect(jsonPath("$.tags[0].tagId").value("tag_couple"))
            .andExpect(jsonPath("$.rating").value(4))
            .andExpect(jsonPath("$.aiSummary").doesNotExist())
    }

    @Test
    fun `남의 저장 조회는 없는 저장과 같게 404다 (S8)`() {
        mockMvc
            .perform(get("/v1/saves/save_9").header(UserIdArgumentResolver.HEADER, "2"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("SAVE_NOT_FOUND"))
    }

    @Test
    fun `이어쓰기 목록은 커서로 중복·누락 없이 끝까지 이어진다`() {
        listMySavesUseCase.seed(count = 5)

        val collected = mutableListOf<String>()
        var cursor: String? = null
        do {
            val body =
                mockMvc
                    .perform(
                        get("/v1/saves")
                            .header(UserIdArgumentResolver.HEADER, "1")
                            .param("limit", "2")
                            .apply { cursor?.let { param("cursor", it) } },
                    ).andExpect(status().isOk)
                    .andReturn()
                    .response
                    .contentAsString
            collected += Regex("\"saveId\":\"([^\"]+)\"").findAll(body).map { it.groupValues[1] }
            cursor = Regex("\"nextCursor\":\"([^\"]+)\"").find(body)?.groupValues?.get(1)
        } while (cursor != null)

        assertEquals(listOf("save_5", "save_4", "save_3", "save_2", "save_1"), collected)
    }

    @Test
    fun `다른 사용자의 커서는 INVALID_CURSOR다`() {
        listMySavesUseCase.seed(count = 5)
        val cursor =
            mockMvc
                .perform(get("/v1/saves").header(UserIdArgumentResolver.HEADER, "1").param("limit", "2"))
                .andReturn()
                .response
                .contentAsString
                .let { Regex("\"nextCursor\":\"([^\"]+)\"").find(it)!!.groupValues[1] }

        mockMvc
            .perform(get("/v1/saves").header(UserIdArgumentResolver.HEADER, "2").param("cursor", cursor))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_CURSOR"))
    }

    private class RecordingUpdateSaveUseCase : UpdateSaveUseCase {
        val commands = mutableListOf<UpdateSaveCommand>()
        var reviewed = false
        var error: ErrorCode? = null

        override fun update(command: UpdateSaveCommand): SaveResult {
            error?.let { throw TmtException(it) }
            commands += command
            return SaveResult(
                saveId = command.saveId,
                reviewId = if (reviewed) 100L else null,
                placeId = command.placeId ?: 1L,
                grantedCount = if (reviewed) 1 else 0,
                availableCount = if (reviewed) 2 else 1,
            )
        }
    }

    private class RecordingDeleteSaveUseCase : DeleteSaveUseCase {
        val deleted = mutableListOf<Pair<Long, Long>>()
        var error: ErrorCode? = null

        override fun delete(
            userId: Long,
            saveId: Long,
        ) {
            error?.let { throw TmtException(it) }
            deleted += userId to saveId
        }
    }

    /** 소유자는 1번 사용자다 — 그 밖의 조회는 FORBIDDEN. */
    private class StubGetSaveUseCase : GetSaveUseCase {
        override fun get(
            userId: Long,
            saveId: Long,
        ): SaveDetailView {
            if (userId != 1L) throw TmtException(ErrorCode.SAVE_NOT_FOUND)
            return SaveDetailView(
                saveId = saveId,
                reviewId = null,
                place =
                    SaveDetailView.Place(
                        placeId = 1,
                        name = "한판승부",
                        roadAddress = "서울 마포구 도화동 1",
                        categoryName = "한식",
                    ),
                photos =
                    listOf(
                        SaveDetailView.Photo(photoId = 5, url = "https://media.tmt.example/photos/5.jpg", order = 0),
                    ),
                tags = listOf(SaveDetailView.Tag("tag_couple", "연인")),
                rating = 4,
                content = "맛있어요",
                aiSummary = null,
                createdAt = Instant.parse("2026-08-27T00:00:00Z"),
            )
        }
    }

    /** 실제 어댑터와 같이 키셋으로 자른다 — 커서 왕복의 중복·누락을 볼 수 있어야 한다. */
    private class InMemoryListMySavesUseCase : ListMySavesUseCase {
        private var rows = emptyList<MySaveView>()

        fun seed(count: Int) {
            rows =
                (count downTo 1).map {
                    MySaveView(
                        saveId = it.toLong(),
                        placeId = 1,
                        placeName = "한판승부",
                        placeRoadAddress = "서울 마포구 도화동 1",
                        thumbnailUrl = null,
                        updatedAt = Instant.parse("2026-08-27T00:00:00Z").plusSeconds(it.toLong()),
                    )
                }
        }

        override fun list(request: MySavesRequest): MySavesResult {
            val after = request.after
            val remaining =
                rows.filter {
                    after == null ||
                        it.updatedAt < after.updatedAt ||
                        (it.updatedAt == after.updatedAt && it.saveId < after.saveId)
                }
            return MySavesResult(
                items = remaining.take(request.limit),
                hasNext = remaining.size > request.limit,
            )
        }
    }

    private class RecordingCreateSaveUseCase : CreateSaveUseCase {
        val commands = mutableListOf<CreateSaveCommand>()
        var reviewed = false

        override fun create(command: CreateSaveCommand): SaveResult {
            commands += command
            return SaveResult(
                saveId = commands.size.toLong(),
                reviewId = if (reviewed) 100L else null,
                // 직접 등록이면 새 매장 ID가 나간다 — 어댑터가 INSERT ... RETURNING id로 받는 값이다
                placeId = (command.place as? PlaceSelection.Existing)?.placeId ?: 900L,
                grantedCount = if (reviewed) 1 else 0,
                availableCount = if (reviewed) 2 else 1,
            )
        }
    }

    /** juso 승인키가 없어 실제 좌표 API는 부르지 않는다 — 포트를 페이크로 세운다. */
    private class FakeCoordinateUseCase : ResolveAddressCoordinateUseCase {
        val calls = mutableListOf<AddressCoordinateKey>()
        var failure: RuntimeException? = null

        override fun resolve(key: AddressCoordinateKey): Wgs84Point {
            failure?.let { throw it }
            calls += key
            return Wgs84Point(latitude = 37.5209, longitude = 126.8641)
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
