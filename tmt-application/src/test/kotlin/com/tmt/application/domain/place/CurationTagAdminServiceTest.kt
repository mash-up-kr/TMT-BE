package com.tmt.application.domain.place

import com.tmt.application.port.input.CurationTagCreateCommand
import com.tmt.application.port.input.CurationTagUpdateCommand
import com.tmt.application.port.output.persistence.CurationTagAdminPort
import com.tmt.application.port.output.persistence.CurationTagAdminRow
import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * 검증 상한은 명세에 없어 서비스에서 정한 값이다 — 이 테스트가 그 값을 고정한다.
 * 상한을 바꾸려면 계약 변경 이력 §4의 해당 행과 함께 고친다.
 */
class CurationTagAdminServiceTest {
    private val port = mockk<CurationTagAdminPort>()
    private val service = CurationTagAdminService(port)

    private fun row(
        id: String = "curation_test",
        label: String = "문구",
        displayOrder: Int = 1,
        active: Boolean = true,
        placeCount: Int = 0,
    ) = CurationTagAdminRow(id, label, displayOrder, active, placeCount)

    @Test
    fun `칩 id는 curation_ 접두와 영소문자·숫자·밑줄만 받는다`() {
        // 이 값이 그대로 검색·핀의 curationTagId 파라미터라 접두가 섞이면 칩인지 구분할 수 없다
        listOf("mapo_pork", "curation_", "curation_Mapo", "curation_마포", "curation_pork!", "curation_$LONG_SUFFIX")
            .forEach { badId ->
                val e =
                    assertFailsWith<TmtException> {
                        service.create(CurationTagCreateCommand(badId, "문구", 1))
                    }
                assertEquals(ErrorCode.VALIDATION_FAILED, e.errorCode, "허용되면 안 되는 id: $badId")
            }
    }

    @Test
    fun `label은 trim 후 1~30자다`() {
        listOf("", "   ", "가".repeat(31)).forEach { badLabel ->
            val e =
                assertFailsWith<TmtException> {
                    service.create(CurationTagCreateCommand("curation_ok", badLabel, 1))
                }
            assertEquals(ErrorCode.VALIDATION_FAILED, e.errorCode)
        }
    }

    @Test
    fun `label의 앞뒤 공백은 잘라서 저장한다`() {
        every { port.createTag("curation_ok", "을지로 야장", 1) } returns true
        every { port.findTag("curation_ok") } returns row(label = "을지로 야장")

        service.create(CurationTagCreateCommand("curation_ok", "  을지로 야장  ", 1))

        verify { port.createTag("curation_ok", "을지로 야장", 1) }
    }

    @Test
    fun `displayOrder는 0~999다`() {
        listOf(-1, 1000).forEach { bad ->
            val e =
                assertFailsWith<TmtException> {
                    service.create(CurationTagCreateCommand("curation_ok", "문구", bad))
                }
            assertEquals(ErrorCode.VALIDATION_FAILED, e.errorCode)
        }
    }

    @Test
    fun `같은 id면 덮어쓰지 않고 CURATION_TAG_DUPLICATED다`() {
        every { port.createTag(any(), any(), any()) } returns false

        val e =
            assertFailsWith<TmtException> {
                service.create(CurationTagCreateCommand("curation_dup", "문구", 1))
            }

        assertEquals(ErrorCode.CURATION_TAG_DUPLICATED, e.errorCode)
        verify(exactly = 0) { port.updateTag(any(), any(), any(), any()) }
    }

    @Test
    fun `바꿀 값이 하나도 없으면 400이고 포트를 부르지 않는다`() {
        val e =
            assertFailsWith<TmtException> {
                service.update("curation_ok", CurationTagUpdateCommand())
            }

        assertEquals(ErrorCode.VALIDATION_FAILED, e.errorCode)
        verify(exactly = 0) { port.updateTag(any(), any(), any(), any()) }
    }

    @Test
    fun `없는 칩 수정은 CURATION_TAG_NOT_FOUND다`() {
        every { port.updateTag("curation_none", "문구", null, null) } returns false

        val e =
            assertFailsWith<TmtException> {
                service.update("curation_none", CurationTagUpdateCommand(label = "문구"))
            }

        assertEquals(ErrorCode.CURATION_TAG_NOT_FOUND, e.errorCode)
    }

    @Test
    fun `없는 칩의 매장 목록 조회·교체는 CURATION_TAG_NOT_FOUND다`() {
        every { port.findTag("curation_none") } returns null

        assertEquals(
            ErrorCode.CURATION_TAG_NOT_FOUND,
            assertFailsWith<TmtException> { service.get("curation_none") }.errorCode,
        )
        assertEquals(
            ErrorCode.CURATION_TAG_NOT_FOUND,
            assertFailsWith<TmtException> { service.replace("curation_none", listOf(1L)) }.errorCode,
        )
        verify(exactly = 0) { port.replaceTagPlaces(any(), any()) }
    }

    @Test
    fun `placeIds 중복은 조용히 접지 않고 400이다`() {
        every { port.findTag(any()) } returns row()

        // 접으면 pin_order가 밀려 운영이 보낸 순서와 결과가 달라진다
        val e =
            assertFailsWith<TmtException> {
                service.replace("curation_ok", listOf(1L, 2L, 1L))
            }

        assertEquals(ErrorCode.VALIDATION_FAILED, e.errorCode)
        verify(exactly = 0) { port.replaceTagPlaces(any(), any()) }
    }

    @Test
    fun `매장은 칩 하나에 최대 100곳이다`() {
        every { port.findTag(any()) } returns row()

        val e =
            assertFailsWith<TmtException> {
                service.replace("curation_ok", (1L..(CurationTagAdminService.MAX_PLACES + 1)).toList())
            }

        assertEquals(ErrorCode.VALIDATION_FAILED, e.errorCode)
        verify(exactly = 0) { port.replaceTagPlaces(any(), any()) }
    }

    @Test
    fun `없는 매장이 섞여 있으면 교체를 거부하고 PLACE_NOT_FOUND다`() {
        every { port.findTag(any()) } returns row()
        every { port.findMissingPlaceIds(listOf(1L, 2L)) } returns listOf(2L)

        val e =
            assertFailsWith<TmtException> {
                service.replace("curation_ok", listOf(1L, 2L))
            }

        // FK 위반으로 터지면 어느 매장이 문제인지 응답에 남지 않는다
        assertEquals(ErrorCode.PLACE_NOT_FOUND, e.errorCode)
        verify(exactly = 0) { port.replaceTagPlaces(any(), any()) }
    }

    @Test
    fun `빈 목록 교체는 허용한다 — 칩을 비우는 정상 동작이다`() {
        every { port.findTag(any()) } returns row()
        every { port.findMissingPlaceIds(emptyList()) } returns emptyList()
        every { port.replaceTagPlaces("curation_ok", emptyList()) } returns Unit
        every { port.findTagPlaces("curation_ok") } returns emptyList()

        assertEquals(emptyList(), service.replace("curation_ok", emptyList()))

        verify { port.replaceTagPlaces("curation_ok", emptyList()) }
    }

    companion object {
        /** 접두 뒤 21자 — 20자 상한을 넘긴다 (컬럼 폭 30자 안에 들어야 한다) */
        private const val LONG_SUFFIX = "abcdefghijklmnopqrstu"
    }
}
