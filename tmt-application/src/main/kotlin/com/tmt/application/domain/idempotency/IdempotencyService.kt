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
    override fun <T : Any> execute(
        request: IdempotentRequest<T>,
        businessLogic: () -> T,
    ): IdempotentResult<T> {
        val fingerprint = payloadCodec.fingerprint(request.payload)

        idempotencyPort.find(request.userId, request.endpoint, request.idemKey)?.let {
            return replay(it, fingerprint, request.responseType)
        }

        return try {
            val response =
                idempotentRequestTransaction.runAndRecord(
                    userId = request.userId,
                    endpoint = request.endpoint,
                    idemKey = request.idemKey,
                    requestFingerprint = fingerprint,
                    responseStatus = request.successStatus,
                    businessLogic = businessLogic,
                )
            IdempotentResult(response, request.successStatus, replayed = false)
        } catch (e: IdempotencyRaceLostException) {
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
