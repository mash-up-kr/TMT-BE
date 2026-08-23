package com.tmt.application.domain.idempotency

/**
 * 멱등 키로 남기는 최초 응답. `(userId, endpoint, idemKey)`가 `idempotency_key` 테이블의 PK다.
 *
 * @param responseBody 최초 응답의 JSON. 재요청에는 상태에서 다시 조립하지 않고 이 값을 그대로 돌려준다 —
 *   그 순간에만 유효한 값(이번 요청으로 발급된 티켓 수 등)은 재조립으로 복원되지 않는다.
 */
data class IdempotencyRecord(
    val userId: Long,
    val endpoint: String,
    val idemKey: String,
    val requestFingerprint: String,
    val responseStatus: Int,
    val responseBody: String,
) {
    init {
        require(endpoint.isNotBlank() && endpoint.length <= ENDPOINT_MAX_LENGTH) {
            "endpoint는 1~${ENDPOINT_MAX_LENGTH}자여야 한다: $endpoint"
        }
        require(idemKey.isNotBlank() && idemKey.length <= IDEM_KEY_MAX_LENGTH) {
            "idemKey는 1~${IDEM_KEY_MAX_LENGTH}자여야 한다"
        }
        require(requestFingerprint.length == FINGERPRINT_LENGTH) {
            "requestFingerprint는 ${FINGERPRINT_LENGTH}자여야 한다"
        }
    }

    companion object {
        /** idempotency_key.endpoint VARCHAR(80) */
        const val ENDPOINT_MAX_LENGTH = 80

        /** idempotency_key.idem_key VARCHAR(100) */
        const val IDEM_KEY_MAX_LENGTH = 100

        /** idempotency_key.request_fingerprint VARCHAR(64) — SHA-256 hex */
        const val FINGERPRINT_LENGTH = 64
    }
}

/**
 * 같은 키를 먼저 커밋한 요청이 있어 INSERT가 밀렸다는 신호.
 * 비즈니스 트랜잭션을 롤백시켜야 하므로 [com.tmt.application.port.output.persistence.IdempotencyPort]
 * 구현이 던지고 트랜잭션 경계 바깥에서만 잡는다.
 */
class IdempotencyRaceLostException(
    endpoint: String,
    idemKey: String,
) : RuntimeException("멱등 키 선점에 밀렸다: $endpoint / $idemKey")
