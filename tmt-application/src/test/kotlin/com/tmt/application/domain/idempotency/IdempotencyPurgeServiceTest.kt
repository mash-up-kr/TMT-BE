package com.tmt.application.domain.idempotency

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class IdempotencyPurgeServiceTest {
    private val port = FakeIdempotencyPort()
    private val service = IdempotencyPurgeService(port, Duration.ofDays(1))

    @Test
    fun `TTL만큼 지난 시각을 기준으로 지우고 건수를 돌려준다`() {
        port.deleteResult = 3
        val before = Instant.now()

        val deleted = service.purgeExpired()

        assertEquals(3, deleted)
        val threshold = requireNotNull(port.deletedBefore)
        assertTrue(threshold >= before.minus(Duration.ofDays(1)), "threshold=$threshold")
        assertTrue(threshold <= Instant.now().minus(Duration.ofDays(1)), "threshold=$threshold")
    }

    @Test
    fun `스케줄 진입점도 같은 정리를 수행한다`() {
        service.purgeOnSchedule()

        assertNotNull(port.deletedBefore)
    }
}
