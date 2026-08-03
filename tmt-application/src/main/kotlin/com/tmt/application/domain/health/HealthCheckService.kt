package com.tmt.application.domain.health

import com.tmt.application.port.input.HealthCheckUseCase
import com.tmt.application.port.output.persistence.DatabaseHealthCheckPort
import org.springframework.stereotype.Service

@Service
class HealthCheckService(
    private val databaseHealthCheckPort: DatabaseHealthCheckPort,
) : HealthCheckUseCase {
    override fun checkApiHealth(): Boolean = true

    override fun checkDatabaseHealth(): Boolean = databaseHealthCheckPort.isHealthy()
}
