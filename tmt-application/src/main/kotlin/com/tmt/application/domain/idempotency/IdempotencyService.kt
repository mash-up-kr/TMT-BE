package com.tmt.application.domain.idempotency

import com.tmt.application.port.input.IdempotentRequest
import com.tmt.application.port.input.IdempotentRequestUseCase
import com.tmt.application.port.input.IdempotentResult
import com.tmt.application.port.output.persistence.IdempotencyPort
import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException
import org.springframework.stereotype.Service

/**
 * 최초 응답을 통째로 기록해 두고 재요청에 그대로 돌려준다 (공통 API 규약 §9).
 *
 * 트랜잭션은 [IdempotentRequestTransaction]이 열고, 이 클래스는 그 바깥에 있어야 한다 —
 * 경합에 밀린 요청은 롤백이 끝난 뒤에 다시 조회해야 이긴 요청이 커밋한 레코드를 볼 수 있다.
 */
@Service
class IdempotencyService(
    private val idempotencyPort: IdempotencyPort,
    private val payloadCodec: IdempotencyPayloadCodec,
    private val idempotentRequestTransaction: IdempotentRequestTransaction,
) : IdempotentRequestUseCase {
    override fun <T : Any, P> execute(
        request: IdempotentRequest<T>,
        prepare: () -> P,
        businessLogic: (P) -> T,
    ): IdempotentResult<T> {
        val fingerprint = payloadCodec.fingerprint(request.payload)

        idempotencyPort.find(request.userId, request.endpoint, request.idemKey)?.let {
            return replay(it, fingerprint, request.responseType)
        }

        // 트랜잭션 밖이다 — 외부 I/O가 커넥션을 잡은 채 대기하지 않는다. 재요청은 위에서 끊겨
        // 여기까지 오지 않으므로 준비 작업도 다시 돌지 않는다
        val prepared = prepare()

        return try {
            val response =
                idempotentRequestTransaction.runAndRecord(
                    userId = request.userId,
                    endpoint = request.endpoint,
                    idemKey = request.idemKey,
                    requestFingerprint = fingerprint,
                    responseStatus = request.successStatus,
                    businessLogic = { businessLogic(prepared) },
                )
            IdempotentResult(response, request.successStatus, replayed = false)
        } catch (e: IdempotencyRaceLostException) {
            // 밀린 쪽은 승자의 커밋을 기다렸다 rowcount 0을 받으므로 여기서는 승자 레코드가 보인다.
            // null은 그 사이 TTL purge가 지운 경우뿐이라 실질적으로 도달하지 않는다 — 지우면 NPE로
            // 500이 나가므로, 재시도할 수 있게 409로 돌려준다.
            val recorded =
                idempotencyPort.find(request.userId, request.endpoint, request.idemKey)
                    ?: throw TmtException(ErrorCode.IDEMPOTENCY_CONFLICT, "같은 키의 요청이 처리 중입니다. 잠시 후 다시 시도해 주세요.")
            replay(recorded, fingerprint, request.responseType)
        }
    }

    private fun <T : Any> replay(
        record: IdempotencyRecord,
        fingerprint: String,
        responseType: Class<T>,
    ): IdempotentResult<T> {
        if (record.requestFingerprint != fingerprint) {
            throw TmtException(ErrorCode.IDEMPOTENCY_CONFLICT)
        }
        return IdempotentResult(
            response = payloadCodec.deserialize(record.responseBody, responseType),
            status = record.responseStatus,
            replayed = true,
        )
    }
}
