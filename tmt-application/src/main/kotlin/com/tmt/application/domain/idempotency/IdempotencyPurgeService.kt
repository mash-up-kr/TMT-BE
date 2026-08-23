package com.tmt.application.domain.idempotency

import com.tmt.application.port.input.PurgeIdempotencyKeysUseCase
import com.tmt.application.port.output.persistence.IdempotencyPort
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant

private val logger = KotlinLogging.logger {}

/**
 * TTL이 지난 멱등 레코드를 지운다. TTL을 넘긴 재시도는 다시 비즈니스 로직을 타므로
 * 클라이언트 재시도 창(공통 API 규약 §9)보다 넉넉해야 한다.
 */
@Service
class IdempotencyPurgeService(
    private val idempotencyPort: IdempotencyPort,
    @param:Value("\${tmt.idempotency.ttl:P1D}") private val ttl: Duration,
) : PurgeIdempotencyKeysUseCase {
    override fun purgeExpired(): Int {
        val threshold = Instant.now().minus(ttl)
        val deleted = idempotencyPort.deleteCreatedBefore(threshold)
        logger.info { "멱등 키 TTL 정리 - threshold=$threshold, deleted=$deleted" }
        return deleted
    }

    @Scheduled(cron = "\${tmt.idempotency.purge-cron:0 20 4 * * *}", zone = "Asia/Seoul")
    fun purgeOnSchedule() {
        purgeExpired()
    }
}
