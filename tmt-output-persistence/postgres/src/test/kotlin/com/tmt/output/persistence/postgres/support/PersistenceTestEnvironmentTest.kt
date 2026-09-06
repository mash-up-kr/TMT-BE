package com.tmt.output.persistence.postgres.support

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 통합 테스트 바닥이 실제로 서 있는지 확인한다 (TMT-295).
 * 여기가 깨지면 컨테이너·Flyway·슬라이스 구성 중 하나가 어긋난 것이다.
 */
class PersistenceTestEnvironmentTest : PersistenceTest() {
    @Test
    fun `PostGIS 확장이 올라와 있다`() {
        val installed =
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pg_extension WHERE extname = 'postgis'",
                Int::class.java,
            )
        assertEquals(1, installed)
    }

    @Test
    fun `Flyway 마이그레이션이 전부 적용됐다`() {
        val pending =
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success = false",
                Int::class.java,
            )
        assertEquals(0, pending)

        val applied =
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success = true",
                Int::class.java,
            )!!
        assertTrue(applied > 0, "적용된 마이그레이션이 없다")
    }

    @Test
    fun `place location이 geography 4326이다`() {
        val srid =
            jdbcTemplate.queryForObject(
                "SELECT srid FROM geography_columns WHERE f_table_name = 'place' AND f_geography_column = 'location'",
                Int::class.java,
            )
        assertEquals(4326, srid)
    }

    @Test
    fun `롤백이 꺼져 있어 커밋이 남는다`() {
        // nickname은 2~10자 제약이 있어(U3) 마커로 못 쓴다. UNIQUE인 kakao_id로 이 테스트 행을 가른다
        val marker = System.nanoTime()
        jdbcTemplate.update(
            "INSERT INTO users (kakao_id, nickname) VALUES (?, ?)",
            marker,
            "tmt295",
        )
        val found =
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM users WHERE kakao_id = ?",
                Int::class.java,
                marker,
            )
        assertEquals(1, found)
    }
}
