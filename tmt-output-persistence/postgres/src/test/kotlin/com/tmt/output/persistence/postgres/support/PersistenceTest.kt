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
 * `ddl-auto`는 운영·로컬과 같은 `validate`로 고정한다 — 슬라이스 기본값(none)으로 두면
 * 엔티티와 마이그레이션이 어긋나도 테스트가 통과한다.
 *
 * **트랜잭션 롤백을 끈다.** 롤백을 두면 커밋이 없어 동시성 경합을
 * 재현할 수 없고(TMT-227), 커밋 이후에 도는 로직도 검증되지 않는다. 대신 테스트끼리 데이터가
 * 남으므로 각 테스트가 자기 데이터를 만들고 그 id로만 단언한다 — 전역 집계에 단언하면 깨진다.
 *
 * Boot 4는 test 자동설정을 모듈별로 쪼갰다. `@DataJpaTest`는 `spring-boot-starter-test`가 아니라
 * `spring-boot-starter-data-jpa-test`에 있고, `@AutoConfigureTestDatabase`는 또 다른 jdbc 쪽이다 —
 * import 경로가 Boot 3 문서와 다르다.
 */
@DataJpaTest(properties = ["spring.jpa.hibernate.ddl-auto=validate"])
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
abstract class PersistenceTest {
    @Autowired
    protected lateinit var jdbcTemplate: JdbcTemplate

    /** 테스트가 쓸 행을 만드는 곳. 규칙은 [PersistenceFixtures] 참고. */
    protected val fixtures: PersistenceFixtures by lazy { PersistenceFixtures(jdbcTemplate) }

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun datasource(registry: DynamicPropertyRegistry) = PostgisContainer.register(registry)
    }
}
