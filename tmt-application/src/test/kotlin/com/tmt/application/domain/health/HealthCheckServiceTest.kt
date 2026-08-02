package com.tmt.application.domain.health

import com.tmt.application.port.output.persistence.DatabaseHealthCheckPort
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HealthCheckServiceTest {
    private val databaseHealthCheckPort = mockk<DatabaseHealthCheckPort>()
    private val service = HealthCheckService(databaseHealthCheckPort)

    @Test
    fun `API 헬스 체크는 항상 정상을 반환한다`() {
        assertTrue(service.checkApiHealth())
    }

    @Test
    fun `DB가 정상이면 DB 헬스 체크도 정상이다`() {
        every { databaseHealthCheckPort.isHealthy() } returns true

        assertTrue(service.checkDatabaseHealth())
    }

    @Test
    fun `DB가 비정상이면 DB 헬스 체크도 비정상이다`() {
        every { databaseHealthCheckPort.isHealthy() } returns false

        assertFalse(service.checkDatabaseHealth())
    }
}
