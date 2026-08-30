package com.tmt.output.persistence.postgres.support

import org.springframework.test.context.DynamicPropertyRegistry
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

/**
 * 통합 테스트가 붙는 PostGIS 컨테이너 (TMT-295).
 *
 * **모듈 전체가 하나를 공유한다.** 테스트 클래스마다 띄우면 클래스 수만큼 기동돼 CI 시간이 배로 늘어난다.
 *
 * 스키마는 Flyway가 올린다 — 마이그레이션 자체가 검증 대상이다.
 *
 * 테스트끼리 데이터를 지우지 않는다. 각 테스트가 자기 데이터를 새로 만들고 그 id로만 단언한다
 * (전역 집계에 단언하지 말 것). 컨테이너에 앞선 실행의 데이터가 남아 있어도 결과가 같아야 한다.
 */
object PostgisContainer {
    // 로컬 docker-compose.local.yml과 같은 이미지를 쓴다 — 버전이 갈리면 실행 계획이 달라진다
    private const val IMAGE = "imresamu/postgis:16-3.4"

    private val instance =
        // PostGIS 이미지는 postgres가 아니라 Testcontainers가 호환성을 못 알아본다 — 명시해준다
        PostgreSQLContainer<Nothing>(DockerImageName.parse(IMAGE).asCompatibleSubstituteFor("postgres")).apply {
            withDatabaseName("tmt")
            withUsername("tmt")
            withPassword("tmt")
            // 컨테이너를 JVM 종료 후에도 살려 다음 실행에서 재사용한다. 로컬에서
            // ~/.testcontainers.properties에 testcontainers.reuse.enable=true가 있어야 실제로 켜지고,
            // 없으면 매번 새로 뜬다 — 정확성에는 영향이 없고 느릴 뿐이다.
            withReuse(true)
            start()
        }

    /** `@DynamicPropertySource`에서 호출해 컨테이너 접속 정보를 넘긴다. */
    fun register(registry: DynamicPropertyRegistry) {
        registry.add("spring.datasource.url", instance::getJdbcUrl)
        registry.add("spring.datasource.username", instance::getUsername)
        registry.add("spring.datasource.password", instance::getPassword)
    }
}
