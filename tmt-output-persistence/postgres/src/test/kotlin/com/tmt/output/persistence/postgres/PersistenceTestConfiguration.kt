package com.tmt.output.persistence.postgres

import com.tmt.output.persistence.postgres.config.JpaConfig
import org.springframework.boot.SpringBootConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import javax.sql.DataSource

/**
 * 슬라이스 테스트가 위로 올라가며 찾는 설정 진입점 (TMT-295).
 *
 * **모듈 루트 패키지에 둔다.** `@DataJpaTest`는 테스트 클래스의 패키지에서 상위로만 훑어서,
 * 하위 패키지(`adapter` 등)에 테스트를 만들면 형제 패키지(`support`)의 설정은 못 찾는다.
 * persistence 모듈에는 애플리케이션 클래스가 없어 테스트용으로 하나 둔다 — `JpaConfig`의
 * 엔티티 스캔·리포지토리 등록 범위를 그대로 쓴다.
 */
@SpringBootConfiguration
@Import(JpaConfig::class)
class PersistenceTestConfiguration {
    /** 네이티브 SQL 어댑터(예: PostgisCoordinateTransformAdapter)와 검증 쿼리가 쓴다. */
    @Bean
    fun jdbcTemplate(dataSource: DataSource) = JdbcTemplate(dataSource)
}
