package com.tmt.application.port.input

interface HealthCheckUseCase {
    fun checkApiHealth(): Boolean

    fun checkDatabaseHealth(): Boolean
}
