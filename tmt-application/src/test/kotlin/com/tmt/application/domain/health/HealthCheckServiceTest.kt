package com.tmt.application.domain.health

import com.tmt.application.port.output.persistence.DatabaseHealthCheckPort
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
class HealthCheckServiceTest {
    @Mock
    lateinit var databaseHealthCheckPort: DatabaseHealthCheckPort

    @InjectMocks
    lateinit var service: HealthCheckService

    @Test
    fun `API 헬스 체크는 항상 정상을 반환한다`() {
        assertTrue(service.checkApiHealth())
    }

    @Test
    fun `DB가 정상이면 DB 헬스 체크도 정상이다`() {
        whenever(databaseHealthCheckPort.isHealthy()).thenReturn(true)

        assertTrue(service.checkDatabaseHealth())
    }

    @Test
    fun `DB가 비정상이면 DB 헬스 체크도 비정상이다`() {
        whenever(databaseHealthCheckPort.isHealthy()).thenReturn(false)

        assertFalse(service.checkDatabaseHealth())
    }
}
