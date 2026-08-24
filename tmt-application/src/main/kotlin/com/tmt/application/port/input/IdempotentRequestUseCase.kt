package com.tmt.application.port.input

/**
 * 재시도가 중복을 만들 수 있는 요청을 멱등하게 실행한다 (공통 API 규약 §9).
 *
 * 재요청은 [businessLogic]을 다시 타지 않고 최초 응답을 그대로 돌려준다.
 * 호출부가 트랜잭션 경계의 가장 바깥이어야 한다 — 이미 열린 트랜잭션 안에서 부르면
 * 경합에 밀렸을 때 필요한 롤백과 재조회가 동작하지 않는다.
 */
interface IdempotentRequestUseCase {
    fun <T : Any> execute(
        request: IdempotentRequest<T>,
        businessLogic: () -> T,
    ): IdempotentResult<T>
}

/**
 * @param endpoint `POST /v1/saves`처럼 키 공간을 가르는 값. 빠지면 서로 다른 엔드포인트가
 *   키 공간을 공유해 남의 응답이 재현된다.
 * @param payload 요청 바디. 지문 계산 대상이고, 같은 키에 다른 바디가 오면 IDEMPOTENCY_CONFLICT다.
 * @param responseType 기록해 둔 응답 JSON을 되살릴 타입.
 */
data class IdempotentRequest<T : Any>(
    val userId: Long,
    val endpoint: String,
    val idemKey: String,
    val payload: Any?,
    val responseType: Class<T>,
    val successStatus: Int,
)

/** @param replayed 최초 응답을 재현한 것이면 true. */
data class IdempotentResult<T : Any>(
    val response: T,
    val status: Int,
    val replayed: Boolean,
)
