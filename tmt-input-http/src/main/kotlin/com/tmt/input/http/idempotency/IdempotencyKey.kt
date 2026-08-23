package com.tmt.input.http.idempotency

/**
 * 컨트롤러 파라미터에 붙여 `Idempotency-Key` 헤더 값을 주입받는다 (공통 API 규약 §9).
 *
 * - `@IdempotencyKey key: String` — 필수. 헤더가 없거나 비었으면 400 VALIDATION_FAILED
 * - `@IdempotencyKey key: String?` — 선택. 헤더가 없으면 null
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class IdempotencyKey
