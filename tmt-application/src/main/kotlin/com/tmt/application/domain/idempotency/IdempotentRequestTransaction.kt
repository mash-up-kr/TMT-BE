package com.tmt.application.domain.idempotency

import com.tmt.application.port.output.persistence.IdempotencyPort
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 비즈니스 로직과 멱등 레코드를 한 트랜잭션에서 커밋한다 — 따로 커밋하면
 * "티켓은 나갔는데 기록은 없음"(또는 그 반대)이 생긴다.
 *
 * **키 선점이 비즈니스 로직보다 먼저다 (TMT-351).** 같은 키의 후행은 선점 INSERT에서 승자의
 * 커밋을 기다렸다 [IdempotencyRaceLostException]으로 밀려나 재현 경로를 탄다. 로직을 먼저 돌리면
 * 후행이 로직 안의 상호배제(SKIP LOCKED 소비·조건부 INSERT)에서 비즈니스 예외로 먼저 터져,
 * 재현 대신 409가 그대로 나간다 — 타임아웃 재시도가 정확히 그 모양이다 (규약 §9).
 *
 * 그 대가로 후행의 유니크 대기가 한 문장에서 승자 트랜잭션 전체로 늘었다. 지금은 로직이 전부
 * DB라 수 ms지만, **멱등 로직에 외부 I/O가 들어오게 되면 lock_timeout과 함께 재검토해야 한다**
 * — 승자가 오래 물리면 후행이 톰캣 스레드와 커넥션을 무기한 붙잡는다 (PR 리뷰).
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
        // 자리값 본문은 같은 트랜잭션에서 즉시 덮어쓰므로 밖에서는 보이지 않고,
        // 로직이 실패하면 선점도 함께 롤백돼 실패 응답은 기록되지 않는다
        idempotencyPort.insert(
            IdempotencyRecord(
                userId = userId,
                endpoint = endpoint,
                idemKey = idemKey,
                requestFingerprint = requestFingerprint,
                responseStatus = responseStatus,
                responseBody = PENDING_BODY,
            ),
        )
        val response = businessLogic()
        idempotencyPort.updateResponseBody(userId, endpoint, idemKey, payloadCodec.serialize(response))
        return response
    }

    companion object {
        private const val PENDING_BODY = "{}"
    }
}
