package com.tmt.output.persistence.postgres.support

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * persistence 어댑터 통합 테스트의 바닥 (TMT-295).
 *
 * 어댑터는 `@Component`라 슬라이스가 잡지 않는다 — 필요한 것만 `@Import`로 얹는다.
 *
 * **트랜잭션 롤백을 끈다.** `@DataJpaTest`의 기본인 롤백을 두면 커밋이 없어 동시성 경합을
 * 재현할 수 없고(TMT-227), 커밋 이후에 도는 로직도 검증되지 않는다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
abstract class PersistenceTest {
    @Autowired
    protected lateinit var jdbcTemplate: JdbcTemplate

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun datasource(registry: DynamicPropertyRegistry) = PostgisContainer.register(registry)
    }
}
