package com.tmt.output.persistence.postgres.adapter

import com.tmt.output.persistence.postgres.support.PersistenceTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import

/**
 * 좌표계 변환을 PostGIS에 맡긴 부분 (P4).
 *
 * 변환식 자체가 아니라 **배선**이 검증 대상이다 — srid 인자가 실제로 쓰이는지, 결과가
 * `ST_Y`=위도 / `ST_X`=경도 순서로 나오는지. 둘 다 틀려도 쿼리는 통과하고 값만 조용히 어긋난다.
 */
@Import(PostgisCoordinateTransformAdapter::class)
class PostgisCoordinateTransformAdapterTest : PersistenceTest() {
    @Autowired
    private lateinit var adapter: PostgisCoordinateTransformAdapter

    @Test
    fun `juso 좌표계에서 온 값이 원래 위경도로 돌아온다`() {
        val (x, y) = projected(EPSG_5179)

        val point = adapter.toWgs84(x = x, y = y, sourceSrid = EPSG_5179)

        assertEquals(SEOUL_CITY_HALL_LAT, point.latitude, 1e-6)
        assertEquals(SEOUL_CITY_HALL_LNG, point.longitude, 1e-6)
    }

    @Test
    fun `적재분 좌표계도 같은 자리로 돌아온다`() {
        val (x, y) = projected(EPSG_5174)

        val point = adapter.toWgs84(x = x, y = y, sourceSrid = EPSG_5174)

        assertEquals(SEOUL_CITY_HALL_LAT, point.latitude, 1e-6)
        assertEquals(SEOUL_CITY_HALL_LNG, point.longitude, 1e-6)
    }

    @Test
    fun `srid를 다르게 주면 결과가 달라진다`() {
        val (x, y) = projected(EPSG_5179)

        val correct = adapter.toWgs84(x, y, EPSG_5179)
        val wrong = adapter.toWgs84(x, y, EPSG_5174)

        // 같은 수치를 다른 좌표계로 읽으면 다른 자리가 된다 — srid 인자가 상수로 굳지 않았다는 근거
        assertTrue(
            kotlin.math.abs(correct.latitude - wrong.latitude) > 1e-6 ||
                kotlin.math.abs(correct.longitude - wrong.longitude) > 1e-6,
            "srid를 바꿨는데 결과가 같다 — 인자가 쓰이지 않는다",
        )
    }

    /** 서울시청을 대상 좌표계로 미리 옮긴 값. 변환의 반대 방향을 PostGIS로 만든다. */
    private fun projected(srid: Int): Pair<Double, Double> =
        jdbcTemplate.queryForObject(
            """
            SELECT ST_X(g) AS x, ST_Y(g) AS y
            FROM (SELECT ST_Transform(ST_SetSRID(ST_MakePoint(?, ?), 4326), ?) AS g) t
            """.trimIndent(),
            { rs, _ -> rs.getDouble("x") to rs.getDouble("y") },
            SEOUL_CITY_HALL_LNG,
            SEOUL_CITY_HALL_LAT,
            srid,
        )!!

    companion object {
        private const val EPSG_5179 = 5179
        private const val EPSG_5174 = 5174
        private const val SEOUL_CITY_HALL_LAT = 37.5666
        private const val SEOUL_CITY_HALL_LNG = 126.9784
    }
}
