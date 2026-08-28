package com.tmt.application.port.output.persistence

data class Wgs84Point(
    val latitude: Double,
    val longitude: Double,
)

/**
 * 평면좌표 → WGS84(EPSG:4326) 변환. place.location 이 geography(Point,4326)이라 필수다 (P4).
 * 구현은 PostGIS ST_Transform — 변환 라이브러리를 새로 넣지 않기 위해 이미 있는 것을 쓴다.
 */
interface CoordinateTransformPort {
    fun toWgs84(
        x: Double,
        y: Double,
        sourceSrid: Int,
    ): Wgs84Point
}
