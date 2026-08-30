package com.tmt.application.port.output.persistence

/**
 * 사용자가 직접 등록한 매장의 INSERT (F §4-1, P8). 호출부가 연 트랜잭션에 참여한다 (TX-1).
 *
 * `location`이 `geography(Point,4326) NOT NULL`이라 구현은 네이티브 SQL이다 — 자세한 근거는
 * `PlaceCommandAdapter`에 있다.
 */
interface PlaceCommandPort {
    fun insertManualPlace(place: NewPlaceRow): Long
}

/**
 * @param externalId 직접 등록분은 UUID v4다. `UNIQUE (external_source, external_id)`가 붙어 있지만
 *   값이 매번 달라 재시도 중복은 막지 못한다 — 방어선은 `Idempotency-Key`뿐이다 (F §4-1).
 */
data class NewPlaceRow(
    val externalId: String,
    val name: String,
    val roadAddress: String,
    val jibunAddress: String?,
    val regionName: String,
    val categoryId: String?,
    val latitude: Double,
    val longitude: Double,
)
