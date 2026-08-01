package com.tmt.application.port.output.persistence

interface DatabaseHealthCheckPort {
    fun isHealthy(): Boolean
}
