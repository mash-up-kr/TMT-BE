package com.tmt.output.persistence.postgres.adapter

import com.tmt.application.port.output.persistence.CoordinateTransformPort
import com.tmt.application.port.output.persistence.Wgs84Point
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

/**
 * 좌표계 변환을 PostGIS에 맡긴다 (P4). juso 좌표는 EPSG:5179이고 place.location 은 4326이라
 * 변환이 필수인데, PostGIS가 이미 있어 proj 계열 변환 라이브러리를 새로 넣을 이유가 없다.
 * 변환 파라미터도 DB 하나에서 관리돼 적재분(5174)과 경로가 섞이지 않는다.
 */
@Component
class PostgisCoordinateTransformAdapter(
    private val jdbcTemplate: JdbcTemplate,
) : CoordinateTransformPort {
    override fun toWgs84(
        x: Double,
        y: Double,
        sourceSrid: Int,
    ): Wgs84Point =
        jdbcTemplate.queryForObject<Wgs84Point>(
            """
            SELECT ST_Y(g) AS latitude, ST_X(g) AS longitude
            FROM (SELECT ST_Transform(ST_SetSRID(ST_MakePoint(?, ?), ?), 4326) AS g) t
            """.trimIndent(),
            { rs, _ -> Wgs84Point(latitude = rs.getDouble("latitude"), longitude = rs.getDouble("longitude")) },
            x,
            y,
            sourceSrid,
        )!!
}
