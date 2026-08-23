package com.tmt.application.port.input

interface PurgeIdempotencyKeysUseCase {
    /** TTL이 지난 멱등 레코드를 지우고 건수를 돌려준다. */
    fun purgeExpired(): Int
}
