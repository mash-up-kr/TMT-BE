package com.tmt.output.persistence.postgres.adapter

import com.tmt.output.persistence.postgres.support.PersistenceTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * 어댑터 패키지에서도 슬라이스가 뜨는지 확인한다 (TMT-295).
 *
 * `@DataJpaTest`는 `@SpringBootConfiguration`을 테스트 클래스의 패키지에서 위로만 훑는다.
 * 설정 클래스가 형제 패키지에 있으면 여기서 `Unable to find a @SpringBootConfiguration`이 난다 —
 * 실제 어댑터 테스트(TMT-208·227)가 놓일 자리라 그 전에 여기서 걸린다.
 */
class AdapterSliceContextTest : PersistenceTest() {
    @Test
    fun `어댑터 패키지에서 컨텍스트가 뜬다`() {
        assertEquals(1, jdbcTemplate.queryForObject("SELECT 1", Int::class.java))
    }
}
