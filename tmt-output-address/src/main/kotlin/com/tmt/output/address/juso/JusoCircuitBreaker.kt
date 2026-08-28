package com.tmt.output.address.juso

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

private val logger = KotlinLogging.logger {}

/**
 * juso 실패에 재시도 정책을 붙이지 않는다 (F §2-3). IP 차단 상태에서 계속 때리면 상황이 나빠지기만 하고,
 * 차단은 유선 신청으로만 풀린다. 연속 실패가 임계치를 넘으면 차단기를 열어 **호출 자체를 멈춘다.**
 */
@Component
class JusoCircuitBreaker(
    @param:Value("\${tmt.address.juso.circuit.failure-threshold:5}") private val failureThreshold: Int,
    @param:Value("\${tmt.address.juso.circuit.open-seconds:60}") private val openSeconds: Long,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val consecutiveFailures = AtomicInteger(0)
    private val openedUntil = AtomicReference<Instant?>(null)

    val isOpen: Boolean
        get() {
            val until = openedUntil.get() ?: return false
            if (clock.instant().isBefore(until)) return true
            // 차단 시간이 지나면 닫고 한 번 더 시도해본다
            openedUntil.set(null)
            consecutiveFailures.set(0)
            return false
        }

    fun recordSuccess() {
        consecutiveFailures.set(0)
        openedUntil.set(null)
    }

    fun recordFailure() {
        val failures = consecutiveFailures.incrementAndGet()
        if (failures >= failureThreshold) {
            openedUntil.set(clock.instant().plus(Duration.ofSeconds(openSeconds)))
            logger.error { "juso 차단기 open - consecutiveFailures=$failures, ${openSeconds}초 동안 호출을 멈춘다" }
        }
    }
}
