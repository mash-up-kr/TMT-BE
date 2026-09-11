package com.tmt.output.persistence.postgres.adapter

import com.tmt.output.persistence.postgres.support.PersistenceTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 큐레이션 칩 taxonomy (E12, V9).
 *
 * `findActiveTags`는 사용자로 좁혀지지 않는 **전역 조회**다. 컨테이너를 공유하므로(TMT-295)
 * 전체 건수에 단언하지 않고 **시드된 칩이 있나 · 내가 만든 칩이 어떻게 보이나**로만 본다.
 */
@Import(CurationTagAdapter::class)
class CurationTagAdapterTest : PersistenceTest() {
    @Autowired
    private lateinit var adapter: CurationTagAdapter

    @Test
    fun `시드된 칩 4종이 display_order 순으로 나온다`() {
        // V10이 넣은 값 — 이 id가 그대로 검색·핀의 curationTagId 조건이 된다.
        // 다른 테스트가 만든 칩이 섞이므로 이 넷의 **상대 순서**만 본다
        val seededIds =
            listOf("curation_euljiro_yajang", "curation_ganmaek", "curation_butteotteok", "curation_lamb")
        val seeded = adapter.findActiveTags().filter { it.curationTagId in seededIds }

        assertEquals(seededIds, seeded.map { it.curationTagId })
        assertEquals("을지로 야장", seeded.first().label)
    }

    @Test
    fun `비활성 칩은 목록에도 없고 존재 확인도 false다`() {
        // 삭제 대신 내린다 — review_tag_definition과 같은 방식이다
        val inactive = fixtures.newCurationTag(label = "내린칩", active = false)

        assertFalse(adapter.findActiveTags().any { it.curationTagId == inactive })
        assertFalse(adapter.existsActiveTag(inactive))
    }

    @Test
    fun `활성 칩은 존재 확인이 true다`() {
        val active = fixtures.newCurationTag(label = "살아있는칩")

        assertTrue(adapter.existsActiveTag(active))
    }

    @Test
    fun `없는 칩 id는 false다 — 예외가 아니다`() {
        assertFalse(adapter.existsActiveTag("curation_does_not_exist"))
    }

    @Test
    fun `매장이 0곳인 칩도 목록에는 나온다`() {
        // 목록(B §2-4)과 검색 결과는 별개다 — 빈 칩은 눌러도 0건이지만 칩 자체는 보인다
        val emptyChip = fixtures.newCurationTag(placeIds = emptyList(), label = "빈칩")

        assertTrue(adapter.findActiveTags().any { it.curationTagId == emptyChip })
    }
}
