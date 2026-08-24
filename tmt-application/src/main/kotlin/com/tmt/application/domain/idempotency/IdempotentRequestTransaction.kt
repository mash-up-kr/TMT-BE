package com.tmt.application.domain.idempotency

import com.tmt.application.port.output.persistence.IdempotencyPort
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 비즈니스 로직과 멱등 레코드를 한 트랜잭션에서 커밋한다 — 따로 커밋하면
 * "티켓은 나갔는데 기록은 없음"(또는 그 반대)이 생긴다.
 *
 * 경합에 밀리면 [IdempotencyRaceLostException]이 이 경계를 뚫고 나가 비즈니스 작업까지 롤백시킨다.
 * 그래서 [IdempotencyService]와 별도 빈이다 — 같은 클래스 안에서 부르면 프록시를 안 타 경계가 생기지 않는다.
 */
@Component
class IdempotentRequestTransaction(
    private val idempotencyPort: IdempotencyPort,
    private val payloadCodec: IdempotencyPayloadCodec,
) {
    @Transactional
    fun <T : Any> runAndRecord(
        userId: Long,
        endpoint: String,
        idemKey: String,
        requestFingerprint: String,
        responseStatus: Int,
        businessLogic: () -> T,
    ): T {
        val response = businessLogic()
        idempotencyPort.insert(
            IdempotencyRecord(
                userId = userId,
                endpoint = endpoint,
                idemKey = idemKey,
                requestFingerprint = requestFingerprint,
                responseStatus = responseStatus,
                responseBody = payloadCodec.serialize(response),
            ),
        )
        return response
    }
}
