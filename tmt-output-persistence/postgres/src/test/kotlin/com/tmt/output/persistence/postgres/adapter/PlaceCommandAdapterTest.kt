package com.tmt.output.persistence.postgres.adapter

import com.tmt.application.port.output.persistence.NewPlaceRow
import com.tmt.output.persistence.postgres.support.PersistenceFixtures
import com.tmt.output.persistence.postgres.support.PersistenceTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import

/**
 * 직접 등록 매장 INSERT (TMT-193). `location`이 NOT NULL이라 JPA를 안 거치고 네이티브로 쓴다 —
 * 좌표가 `ST_MakePoint(경도, 위도)` 순서로 들어가는지, RETURNING id가 실제 행을 가리키는지 본다.
 */
@Import(PlaceCommandAdapter::class)
class PlaceCommandAdapterTest : PersistenceTest() {
    @Autowired
    private lateinit var adapter: PlaceCommandAdapter

    @Test
    fun `등록한 매장이 넣은 값 그대로 남는다`() {
        val row = newPlaceRow()

        val placeId = adapter.insertManualPlace(row)

        val stored =
            jdbcTemplate.queryForObject(
                """
                SELECT external_source, external_id, name, road_address, jibun_address,
                       region_name, category_id, phone_number, review_count, rating_sum,
                       ST_Y(location::geometry) AS latitude, ST_X(location::geometry) AS longitude
                FROM place WHERE id = ?
                """.trimIndent(),
                { rs, _ ->
                    StoredPlace(
                        externalSource = rs.getString("external_source"),
                        externalId = rs.getString("external_id"),
                        name = rs.getString("name"),
                        roadAddress = rs.getString("road_address"),
                        jibunAddress = rs.getString("jibun_address"),
                        regionName = rs.getString("region_name"),
                        categoryId = rs.getString("category_id"),
                        phoneNumber = rs.getString("phone_number"),
                        reviewCount = rs.getInt("review_count"),
                        ratingSum = rs.getLong("rating_sum"),
                        latitude = rs.getDouble("latitude"),
                        longitude = rs.getDouble("longitude"),
                    )
                },
                placeId,
            )!!

        // 적재분('LOCALDATA'·'SEMAS')과 갈라지는 값 (F §7)
        assertEquals("MANUAL", stored.externalSource)
        assertEquals(row.externalId, stored.externalId)
        assertEquals(row.name, stored.name)
        assertEquals(row.roadAddress, stored.roadAddress)
        assertEquals(row.jibunAddress, stored.jibunAddress)
        assertEquals(row.regionName, stored.regionName)
        assertEquals(row.categoryId, stored.categoryId)
        // 직접 등록 화면에 전화번호 입력이 없다
        assertNull(stored.phoneNumber)
        // 좌표가 뒤집히면 서울이 아닌 자리에 꽂힌다
        assertEquals(row.latitude, stored.latitude, 1e-6)
        assertEquals(row.longitude, stored.longitude, 1e-6)
        // 집계는 컬럼 기본값에서 시작한다
        assertEquals(0, stored.reviewCount)
        assertEquals(0L, stored.ratingSum)
    }

    @Test
    fun `카테고리 매핑에 실패한 매장도 등록된다`() {
        val placeId = adapter.insertManualPlace(newPlaceRow(categoryId = null, jibunAddress = null))

        val categoryId =
            jdbcTemplate.queryForObject("SELECT category_id FROM place WHERE id = ?", String::class.java, placeId)

        assertNull(categoryId)
    }

    @Test
    fun `등록한 매장에 바로 저장을 붙일 수 있다`() {
        // FK가 걸린 두 쓰기의 순서 — INSERT가 즉시 나가지 않으면 여기서 깨진다
        val placeId = adapter.insertManualPlace(newPlaceRow())
        val userId = fixtures.newUser()

        val saveId = fixtures.newSave(userId, placeId)

        assertEquals(
            placeId,
            jdbcTemplate.queryForObject("SELECT place_id FROM save WHERE id = ?", Long::class.java, saveId),
        )
    }

    private fun newPlaceRow(
        categoryId: String? = "korean",
        jibunAddress: String? = "서울특별시 중구 태평로1가 31",
    ) = NewPlaceRow(
        externalId = "manual-${PersistenceFixtures.nextSequence()}",
        name = "직접등록매장",
        roadAddress = "서울특별시 중구 세종대로 110",
        jibunAddress = jibunAddress,
        regionName = "중구 태평로1가",
        categoryId = categoryId,
        latitude = 37.5666,
        longitude = 126.9784,
    )

    private data class StoredPlace(
        val externalSource: String,
        val externalId: String,
        val name: String,
        val roadAddress: String,
        val jibunAddress: String?,
        val regionName: String,
        val categoryId: String?,
        val phoneNumber: String?,
        val reviewCount: Int,
        val ratingSum: Long,
        val latitude: Double,
        val longitude: Double,
    )
}
