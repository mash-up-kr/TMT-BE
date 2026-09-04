package com.tmt.input.http.controller

import com.tmt.application.domain.idempotency.IdempotencyPayloadCodec
import com.tmt.application.domain.idempotency.IdempotencyRaceLostException
import com.tmt.application.domain.idempotency.IdempotencyRecord
import com.tmt.application.domain.idempotency.IdempotencyService
import com.tmt.application.domain.idempotency.IdempotentRequestTransaction
import com.tmt.application.port.input.GetJoinPreviewUseCase
import com.tmt.application.port.input.JoinBlockedReason
import com.tmt.application.port.input.JoinGroupCommand
import com.tmt.application.port.input.JoinGroupResult
import com.tmt.application.port.input.JoinGroupUseCase
import com.tmt.application.port.input.JoinPreviewView
import com.tmt.application.port.input.LeaveGroupUseCase
import com.tmt.application.port.output.persistence.IdempotencyPort
import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TicketShortageException
import com.tmt.common.exception.TmtException
import com.tmt.input.http.auth.UserIdArgumentResolver
import com.tmt.input.http.exception.ExceptionAdvice
import com.tmt.input.http.idempotency.IdempotencyKeyArgumentResolver
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Instant

/** 가입·탈퇴 실구현의 어댑터 계약 — 응답 형태·ID 표기·멱등 재현이 mock과 같은지 지킨다. */
class GroupMembershipControllerTest {
    private var preview =
        JoinPreviewView(
            groupId = 1L,
            name = "성수 커피 탐험대",
            imageUrl = "https://media.example.com/g.jpg",
            availableTicketCount = 1,
            requiredTicketCount = 1,
            blockedReason = null,
        )
    private val joinCommands = mutableListOf<JoinGroupCommand>()
    private var joinBehavior: (JoinGroupCommand) -> JoinGroupResult = { command ->
        JoinGroupResult(
            groupId = command.groupId,
            joinedAt = Instant.parse("2026-09-04T00:00:00Z"),
            sharedReviewIds = command.sourceReviewIds,
            consumedCount = 1,
            availableCount = 0,
        )
    }
    private val leaves = mutableListOf<Pair<Long, Long>>()

    private val codec = IdempotencyPayloadCodec()
    private val idempotencyPort = InMemoryIdempotencyPort()

    private val mockMvc: MockMvc =
        MockMvcBuilders
            .standaloneSetup(
                GroupMembershipController(
                    getJoinPreviewUseCase = GetJoinPreviewUseCase { _, _ -> preview },
                    joinGroupUseCase =
                        JoinGroupUseCase { command ->
                            joinCommands += command
                            joinBehavior(command)
                        },
                    leaveGroupUseCase = LeaveGroupUseCase { groupId, userId -> leaves += groupId to userId },
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

    private fun join(
        body: String? = null,
        key: String = "join-1",
        groupId: String = "group_1",
    ) = mockMvc.perform(
        post("/v1/groups/$groupId/memberships")
            .header(UserIdArgumentResolver.HEADER, "1")
            .header(IdempotencyKeyArgumentResolver.HEADER, key)
            .contentType(MediaType.APPLICATION_JSON)
            .apply { body?.let { content(it) } },
    )

    @Test
    fun `가입 팝업이 mock과 같은 형태로 나간다 — group_ 접두·blockedReason null`() {
        mockMvc
            .perform(get("/v1/groups/group_1/join-preview").header(UserIdArgumentResolver.HEADER, "1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.group.groupId").value("group_1"))
            .andExpect(jsonPath("$.group.name").value("성수 커피 탐험대"))
            .andExpect(jsonPath("$.group.imageUrl").value("https://media.example.com/g.jpg"))
            .andExpect(jsonPath("$.availableTicketCount").value(1))
            .andExpect(jsonPath("$.requiredTicketCount").value(1))
            .andExpect(jsonPath("$.joinable").value(true))
            .andExpect(jsonPath("$.blockedReason").doesNotExist())
    }

    @Test
    fun `가입 팝업 — 막힌 이유는 enum 이름 그대로 나간다`() {
        preview = preview.copy(availableTicketCount = 0, blockedReason = JoinBlockedReason.TICKET_REQUIRED)

        mockMvc
            .perform(get("/v1/groups/group_1/join-preview").header(UserIdArgumentResolver.HEADER, "1"))
            .andExpect(jsonPath("$.joinable").value(false))
            .andExpect(jsonPath("$.blockedReason").value("TICKET_REQUIRED"))
    }

    @Test
    fun `비로그인이면 401이다`() {
        mockMvc.perform(get("/v1/groups/group_1/join-preview")).andExpect(status().isUnauthorized)
        mockMvc.perform(delete("/v1/groups/group_1/memberships/me")).andExpect(status().isUnauthorized)
    }

    @Test
    fun `가입은 201과 Location, rv_ 접두 응답으로 나간다`() {
        join(body = """{ "sourceReviewIds": ["rv_5", "rv_7"] }""")
            .andExpect(status().isCreated)
            .andExpect(header().string("Location", "/v1/groups/group_1/memberships/me"))
            .andExpect(jsonPath("$.groupId").value("group_1"))
            .andExpect(jsonPath("$.joinedAt").value("2026-09-04T00:00:00Z"))
            .andExpect(jsonPath("$.sharedReviewIds[0]").value("rv_5"))
            .andExpect(jsonPath("$.sharedReviewIds[1]").value("rv_7"))
            .andExpect(jsonPath("$.ticket.consumedCount").value(1))
            .andExpect(jsonPath("$.ticket.availableCount").value(0))

        assertEquals(
            JoinGroupCommand(userId = 1L, groupId = 1L, sourceReviewIds = listOf(5L, 7L)),
            joinCommands.single(),
        )
    }

    @Test
    fun `단수 sourceReviewId와 복수를 합집합·중복 제거로 넘긴다 (TMT-241)`() {
        join(body = """{ "sourceReviewId": "rv_5", "sourceReviewIds": ["rv_7", "rv_5"] }""")
            .andExpect(status().isCreated)

        assertEquals(listOf(5L, 7L), joinCommands.single().sourceReviewIds)
    }

    @Test
    fun `바디 없이 보내면 공유 없이 가입만 한다`() {
        join().andExpect(status().isCreated).andExpect(jsonPath("$.sharedReviewIds").isEmpty)

        assertEquals(emptyList<Long>(), joinCommands.single().sourceReviewIds)
    }

    @Test
    fun `같은 키로 재시도하면 유스케이스를 다시 타지 않고 최초 응답을 재현한다 (규약 §9)`() {
        val body = """{ "sourceReviewIds": ["rv_5"] }"""
        join(body = body).andExpect(status().isCreated)

        join(body = body)
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.sharedReviewIds[0]").value("rv_5"))
            .andExpect(jsonPath("$.ticket.consumedCount").value(1))

        assertEquals(1, joinCommands.size, "재시도는 가입 로직을 다시 타면 안 된다")
    }

    @Test
    fun `같은 키에 다른 바디면 IDEMPOTENCY_CONFLICT다`() {
        join(body = """{ "sourceReviewIds": ["rv_5"] }""").andExpect(status().isCreated)

        join(body = """{ "sourceReviewIds": ["rv_7"] }""")
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"))
    }

    @Test
    fun `멱등 키 공간은 그룹별로 갈린다`() {
        join(groupId = "group_1").andExpect(status().isCreated)
        join(groupId = "group_2").andExpect(status().isCreated)

        assertEquals(listOf(1L, 2L), joinCommands.map { it.groupId })
    }

    @Test
    fun `티켓이 없으면 409와 티켓 상태를 함께 내린다`() {
        joinBehavior = { throw TicketShortageException(ErrorCode.GROUP_JOIN_TICKET_REQUIRED, availableCount = 0) }

        join()
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("GROUP_JOIN_TICKET_REQUIRED"))
            .andExpect(jsonPath("$.title").value("그룹 가입에 필요한 티켓이 부족합니다."))
            .andExpect(jsonPath("$.ticket.requiredCount").value(1))
            .andExpect(jsonPath("$.ticket.availableCount").value(0))
            .andExpect(jsonPath("$.ticket.shortageCount").value(1))
    }

    @Test
    fun `실패한 가입은 멱등 기록이 남지 않아 다음 시도가 다시 실행된다`() {
        joinBehavior = { throw TmtException(ErrorCode.ALREADY_GROUP_MEMBER) }
        join().andExpect(status().isConflict).andExpect(jsonPath("$.code").value("ALREADY_GROUP_MEMBER"))

        joinBehavior = { command -> JoinGroupResult(command.groupId, Instant.EPOCH, emptyList(), 1, 0) }
        join().andExpect(status().isCreated)

        assertEquals(2, joinCommands.size)
    }

    @Test
    fun `멱등 키가 없으면 400이다`() {
        mockMvc
            .perform(
                post("/v1/groups/group_1/memberships")
                    .header(UserIdArgumentResolver.HEADER, "1")
                    .contentType(MediaType.APPLICATION_JSON),
            ).andExpect(status().isBadRequest)
    }

    @Test
    fun `잘못된 rv_ 형식은 REVIEW_NOT_FOUND, 잘못된 group_ 형식은 GROUP_NOT_FOUND다`() {
        join(body = """{ "sourceReviewIds": ["review_5"] }""")
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("REVIEW_NOT_FOUND"))

        join(groupId = "nope")
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("GROUP_NOT_FOUND"))
    }

    @Test
    fun `탈퇴는 204이고 group_ 접두를 풀어 넘긴다`() {
        mockMvc
            .perform(delete("/v1/groups/group_7/memberships/me").header(UserIdArgumentResolver.HEADER, "1"))
            .andExpect(status().isNoContent)

        assertEquals(listOf(7L to 1L), leaves)
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
