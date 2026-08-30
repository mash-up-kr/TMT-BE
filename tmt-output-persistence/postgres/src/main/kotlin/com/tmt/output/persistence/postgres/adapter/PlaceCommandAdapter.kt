package com.tmt.output.persistence.postgres.adapter

import com.tmt.application.port.output.persistence.NewPlaceRow
import com.tmt.application.port.output.persistence.PlaceCommandPort
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

/**
 * 직접 등록 매장 INSERT를 네이티브 SQL로 쓴다 (TMT-193).
 *
 * `place.location`은 `geography(Point,4326) NOT NULL`이라 INSERT에 값이 반드시 들어가야 하는데,
 * JPA로 매핑하려면 공간 타입 지원 의존성이 새로 필요하다. PostGIS가 값을 직접 만들 수 있고
 * 좌표 변환(`PostgisCoordinateTransformAdapter`)도 이미 네이티브라, 의존성을 늘리지 않는 쪽을 골랐다.
 *
 * `JdbcTemplate`은 호출부가 연 트랜잭션의 커넥션을 그대로 쓴다. 이 INSERT는 즉시 나가고
 * 뒤이은 `save` INSERT는 IDENTITY라 JPA도 즉시 나가므로, FK가 걸린 두 쓰기의 순서가 유지된다.
 */
@Component
class PlaceCommandAdapter(
    private val jdbcTemplate: JdbcTemplate,
) : PlaceCommandPort {
    override fun insertManualPlace(place: NewPlaceRow): Long =
        jdbcTemplate.queryForObject(
            """
            INSERT INTO place (
                external_source, external_id, name, road_address, jibun_address,
                region_name, category_id, phone_number, location
            ) VALUES (
                ?, ?, ?, ?, ?,
                ?, ?, NULL, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography
            )
            RETURNING id
            """.trimIndent(),
            { rs, _ -> rs.getLong("id") },
            EXTERNAL_SOURCE_MANUAL,
            place.externalId,
            place.name,
            place.roadAddress,
            place.jibunAddress,
            place.regionName,
            place.categoryId,
            place.longitude,
            place.latitude,
        )!!

    companion object {
        /** 적재분('LOCALDATA'·'SEMAS')과 사용자 등록분을 가르는 값 (F §7). */
        private const val EXTERNAL_SOURCE_MANUAL = "MANUAL"
    }
}
