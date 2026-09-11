package com.tmt.output.persistence.postgres.adapter

import com.tmt.output.persistence.postgres.support.PersistenceFixtures
import com.tmt.output.persistence.postgres.support.PersistenceTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 큐레이션 칩 운영 쿼리 (TMT-421). 쓰기가 전부 네이티브(생성의 `ON CONFLICT`, 부분 수정의
 * `COALESCE`)라 기동 시 검증되지 않는다 — 실제 PostGIS에 태워야 컬럼 오타·캐스트 누락이 드러난다.
 *
 * 전역 목록에 단언하지 않는다 — 컨테이너를 공유하므로(TMT-295) 내가 만든 칩만 좁혀서 본다.
 */
@Import(CurationTagAdminAdapter::class)
class CurationTagAdminAdapterTest : PersistenceTest() {
    @Autowired
    private lateinit var adapter: CurationTagAdminAdapter

    private fun newTagId() = "curation_a${PersistenceFixtures.nextSequence()}"

    @Test
    fun `없던 칩은 만들어지고 같은 id로 다시 만들면 false다`() {
        val id = newTagId()

        assertTrue(inTransaction { adapter.createTag(id, "새칩", 7) })
        // 덮어쓰지 않는다 — ON CONFLICT DO NOTHING이라 영향 행이 0이다
        assertFalse(inTransaction { adapter.createTag(id, "다른문구", 9) })

        val tag = adapter.findTag(id)!!
        assertEquals("새칩", tag.label)
        assertEquals(7, tag.displayOrder)
        assertTrue(tag.active)
        assertEquals(0, tag.placeCount)
    }

    @Test
    fun `부분 수정은 보낸 필드만 바꾼다`() {
        val id = newTagId()
        inTransaction { adapter.createTag(id, "원래문구", 3) }

        // label만 — displayOrder·active는 COALESCE가 기존 값을 남긴다
        assertTrue(inTransaction { adapter.updateTag(id, label = "바뀐문구", displayOrder = null, active = null) })
        adapter.findTag(id)!!.let {
            assertEquals("바뀐문구", it.label)
            assertEquals(3, it.displayOrder)
            assertTrue(it.active)
        }

        // active만 내린다 — 삭제 대신이다 (D4)
        assertTrue(inTransaction { adapter.updateTag(id, label = null, displayOrder = null, active = false) })
        adapter.findTag(id)!!.let {
            assertEquals("바뀐문구", it.label)
            assertFalse(it.active)
        }
    }

    @Test
    fun `수정은 updated_at을 앞으로 밀고 없는 칩이면 false다`() {
        val id = newTagId()
        inTransaction { adapter.createTag(id, "문구", 1) }
        val before = updatedAtOf(id)

        assertTrue(inTransaction { adapter.updateTag(id, label = "문구2", displayOrder = null, active = null) })

        // 네이티브 UPDATE는 JPA 감사를 거치지 않아 SQL이 직접 now()를 찍어야 한다
        assertTrue(updatedAtOf(id) >= before, "updated_at이 생성 시점에 멈춰 있다")
        assertFalse(inTransaction { adapter.updateTag(newTagId(), "없음", null, null) })
    }

    @Test
    fun `비활성 칩도 운영 목록과 단건 조회에 나온다`() {
        val id = newTagId()
        inTransaction { adapter.createTag(id, "내릴칩", 2) }
        inTransaction { adapter.updateTag(id, label = null, displayOrder = null, active = false) }

        // 공개 목록(CurationTagAdapter)과 다른 지점 — 내린 칩을 다시 올리려면 보여야 한다
        assertTrue(adapter.findAllTags().any { it.curationTagId == id })
        assertFalse(adapter.findTag(id)!!.active)
    }

    @Test
    fun `없는 칩은 null이다`() {
        assertNull(adapter.findTag(newTagId()))
    }

    @Test
    fun `매장 집합 교체는 보낸 순서가 pin_order가 되고 빠진 매장은 내려간다`() {
        val id = newTagId()
        inTransaction { adapter.createTag(id, "칩", 1) }
        val a = fixtures.newPlace(name = "가게A")
        val b = fixtures.newPlace(name = "가게B")
        val c = fixtures.newPlace(name = "가게C")

        inTransaction { adapter.replaceTagPlaces(id, listOf(a, b)) }
        assertEquals(listOf(a to 1, b to 2), adapter.findTagPlaces(id).map { it.placeId to it.pinOrder })

        // b가 양쪽에 있다 — 같은 PK를 지우고 다시 넣는 경로다. 파생 삭제였다면 flush 순서 때문에
        // 삽입이 먼저 나가 PK 충돌이 난다 (그래서 JPQL 벌크 삭제를 쓴다)
        inTransaction { adapter.replaceTagPlaces(id, listOf(c, b)) }
        assertEquals(listOf(c to 1, b to 2), adapter.findTagPlaces(id).map { it.placeId to it.pinOrder })
        assertFalse(adapter.findTagPlaces(id).any { it.placeId == a })
    }

    @Test
    fun `빈 목록으로 교체하면 칩이 비고 placeCount가 0이 된다`() {
        val id = newTagId()
        inTransaction { adapter.createTag(id, "칩", 1) }
        inTransaction { adapter.replaceTagPlaces(id, listOf(fixtures.newPlace())) }
        assertEquals(1, adapter.findTag(id)!!.placeCount)

        inTransaction { adapter.replaceTagPlaces(id, emptyList()) }

        assertEquals(emptyList(), adapter.findTagPlaces(id))
        assertEquals(0, adapter.findTag(id)!!.placeCount)
    }

    @Test
    fun `매장 목록은 이름과 도로명주소를 함께 내린다`() {
        val id = newTagId()
        inTransaction { adapter.createTag(id, "칩", 1) }
        val place = fixtures.newPlace(name = "별미곱창", roadAddress = "서울특별시 마포구 도화동 1")
        inTransaction { adapter.replaceTagPlaces(id, listOf(place)) }

        adapter.findTagPlaces(id).single().let {
            assertEquals("별미곱창", it.placeName)
            assertEquals("서울특별시 마포구 도화동 1", it.roadAddress)
        }
    }

    @Test
    fun `없는 매장 id만 골라 돌려준다`() {
        val real = fixtures.newPlace()
        val fake = 9_000_000_000L

        assertEquals(listOf(fake), adapter.findMissingPlaceIds(listOf(real, fake)))
        assertEquals(emptyList(), adapter.findMissingPlaceIds(listOf(real)))
        // 빈 입력에 IN ()을 내보내지 않는다 — 문법 오류가 된다
        assertEquals(emptyList(), adapter.findMissingPlaceIds(emptyList()))
    }

    private fun updatedAtOf(curationTagId: String): Instant =
        jdbcTemplate
            .queryForObject(
                "SELECT updated_at FROM curation_tag WHERE id = ?",
                java.sql.Timestamp::class.java,
                curationTagId,
            )!!.toInstant()
}
