package com.tmt.output.persistence.postgres.repository

import com.tmt.output.persistence.postgres.support.PersistenceFixtures.Companion.SEOUL_CITY_HALL_LAT
import com.tmt.output.persistence.postgres.support.PersistenceFixtures.Companion.SEOUL_CITY_HALL_LNG
import com.tmt.output.persistence.postgres.support.PersistenceTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

/**
 * 근처 탐색 네이티브 쿼리 (TMT-228). 공간 술어와 키셋은 Fake로 검증되지 않아 실제 플래너가 필요하다.
 *
 * 전역 결과에 단언하지 않는다 — 컨테이너에 다른 테스트의 매장이 남아 있으므로 반경을 좁히고
 * 자기 매장 id로 결과를 거른다.
 */
class NearbyQueryRepositoryTest : PersistenceTest() {
    @Autowired
    private lateinit var repository: NearbyQueryRepository

    /** 반경 술어를 테스트마다 다른 좌표로 좁혀 다른 테스트의 매장이 섞이지 않게 한다. */
    private fun isolatedPoint(): Pair<Double, Double> {
        // 위도 1도 ≈ 111km. 0.01도씩 남쪽으로 내려가며 테스트끼리 최소 1km 떨어뜨린다
        val offset = OFFSET.getAndIncrement() * 0.01
        return SEOUL_CITY_HALL_LAT - 10 - offset to SEOUL_CITY_HALL_LNG
    }

    @Test
    fun `반경 밖 리뷰는 빠지고 거리는 정수 미터로 나온다`() {
        val (lat, lng) = isolatedPoint()
        val near = fixtures.newPlace(latitude = lat + 0.001, longitude = lng) // 약 111m
        val far = fixtures.newPlace(latitude = lat + 0.01, longitude = lng) // 약 1.1km
        val nearReview = fixtures.newPublishedReview(near).reviewId
        fixtures.newPublishedReview(far)

        val rows = repository.findNearbyReviewRows(lat, lng, 500, null, null, null, 50)

        assertEquals(listOf(nearReview), rows.map { it.getReviewId() })
        val distance = rows.single().getDistanceMeters()!!
        assertTrue(distance in 100..120, "약 111m를 기대했는데 ${distance}m가 나왔다")
    }

    @Test
    fun `삭제된 리뷰는 빠진다`() {
        val (lat, lng) = isolatedPoint()
        val place = fixtures.newPlace(latitude = lat, longitude = lng)
        val alive = fixtures.newPublishedReview(place).reviewId
        fixtures.newPublishedReview(place, deletedAt = java.time.Instant.now())

        val rows = repository.findNearbyReviewRows(lat, lng, 500, null, null, null, 50)

        assertEquals(listOf(alive), rows.map { it.getReviewId() })
    }

    @Test
    fun `거리가 같으면 review_id로 갈리고 커서가 경계를 겹치지 않는다`() {
        val (lat, lng) = isolatedPoint()
        // 같은 매장의 리뷰 셋 — 거리가 모두 같아 tie-breaker만으로 순서가 정해진다
        val place = fixtures.newPlace(latitude = lat, longitude = lng)
        val ids = (1..3).map { fixtures.newPublishedReview(place).reviewId }.sorted()

        val first = repository.findNearbyReviewRows(lat, lng, 500, null, null, null, 2)
        assertEquals(ids.take(2), first.map { it.getReviewId() })

        val last = first.last()
        val second =
            repository.findNearbyReviewRows(
                lat,
                lng,
                500,
                last.getDistanceMeters(),
                last.getReviewId(),
                null,
                2,
            )
        assertEquals(ids.drop(2), second.map { it.getReviewId() })
    }

    @Test
    fun `viewerId가 없으면 favorite은 false다`() {
        val (lat, lng) = isolatedPoint()
        val place = fixtures.newPlace(latitude = lat, longitude = lng)
        val review = fixtures.newPublishedReview(place)
        fixtures.addFavorite(review.userId, place)

        val anonymous = repository.findNearbyReviewRows(lat, lng, 500, null, null, null, 10)
        assertFalse(anonymous.single().getFavorite())

        val viewer = repository.findNearbyReviewRows(lat, lng, 500, null, null, review.userId, 10)
        assertTrue(viewer.single().getFavorite())
    }

    @Test
    fun `사진과 태그는 save_id로 묶여 나오고 태그는 동행이 먼저다`() {
        val place = fixtures.newPlace()
        val review = fixtures.newPublishedReview(place)
        val asset = fixtures.newMediaAsset(review.userId)
        fixtures.attachPhoto(review.saveId, asset, photoOrder = 0)
        // 좋은 점(display_order 1)을 먼저 넣어도 tag_type 정렬이 동행을 앞으로 보낸다
        fixtures.addTag(review.saveId, "tag_tasty")
        fixtures.addTag(review.saveId, "tag_couple")

        val photos = repository.findPhotoRows(listOf(review.saveId))
        assertEquals(listOf(review.saveId), photos.map { it.getSaveId() })

        val tags = repository.findTagRows(listOf(review.saveId))
        assertEquals(listOf("tag_couple", "tag_tasty"), tags.map { it.getTagId() })
    }

    @Test
    fun `요약이 없는 리뷰는 행이 없다`() {
        val place = fixtures.newPlace()
        val summarized = fixtures.newPublishedReview(place).reviewId
        val plain = fixtures.newPublishedReview(place).reviewId
        fixtures.addSummary(summarized, pros = "친절", cons = null)

        val rows = repository.findSummaryRows(listOf(summarized, plain))

        assertEquals(listOf(summarized), rows.map { it.getReviewId() })
        assertEquals("친절", rows.single().getPros())
        assertNull(rows.single().getCons())
    }

    @Test
    fun `핀은 bbox 안에서 리뷰를 가진 매장만 나온다`() {
        val (lat, lng) = isolatedPoint()
        val visible = fixtures.newPlace(latitude = lat, longitude = lng, reviewCount = 1)
        val noReview = fixtures.newPlace(latitude = lat, longitude = lng, reviewCount = 0)
        val outside = fixtures.newPlace(latitude = lat + 1, longitude = lng, reviewCount = 1)

        val pins =
            repository
                .findPins(
                    north = lat + 0.01,
                    south = lat - 0.01,
                    east = lng + 0.01,
                    west = lng - 0.01,
                    centerLat = null,
                    centerLng = null,
                    query = null,
                    queryCategoryCsv = "",
                    categoryId = null,
                    regionPrefix = null,
                    limitPlusOne = 50,
                ).map { it.getPlaceId() }

        assertTrue(visible in pins)
        assertFalse(noReview in pins)
        assertFalse(outside in pins)
    }

    @Test
    fun `핀 검색어는 가게명과 도로명주소를 함께 본다`() {
        val (lat, lng) = isolatedPoint()
        val token = "픽스처${System.nanoTime()}"
        val byName = fixtures.newPlace(name = "$token 식당", latitude = lat, longitude = lng, reviewCount = 1)
        val byAddress =
            fixtures.newPlace(
                name = "다른가게",
                roadAddress = "$token 로 1",
                latitude = lat,
                longitude = lng,
                reviewCount = 1,
            )
        val unrelated = fixtures.newPlace(name = "무관한가게", latitude = lat, longitude = lng, reviewCount = 1)

        val pins =
            repository
                .findPins(
                    north = lat + 0.01,
                    south = lat - 0.01,
                    east = lng + 0.01,
                    west = lng - 0.01,
                    centerLat = null,
                    centerLng = null,
                    query = token,
                    queryCategoryCsv = "",
                    categoryId = null,
                    regionPrefix = null,
                    limitPlusOne = 50,
                ).map { it.getPlaceId() }

        assertEquals(setOf(byName, byAddress), pins.toSet())
        assertFalse(unrelated in pins)
    }

    @Test
    fun `핀은 지역 접두어로 걸러진다`() {
        val (lat, lng) = isolatedPoint()
        val region = "마포구 도화동"
        val inRegion = fixtures.newPlace(regionName = region, latitude = lat, longitude = lng, reviewCount = 1)
        val otherRegion =
            fixtures.newPlace(regionName = "강남구 역삼동", latitude = lat, longitude = lng, reviewCount = 1)

        val pins =
            repository
                .findPins(
                    north = lat + 0.01,
                    south = lat - 0.01,
                    east = lng + 0.01,
                    west = lng - 0.01,
                    centerLat = null,
                    centerLng = null,
                    query = null,
                    queryCategoryCsv = "",
                    categoryId = null,
                    // 구 이름만으로도 그 아래 동이 전부 걸린다 — LIKE 접두어 매칭이다
                    regionPrefix = "마포구",
                    limitPlusOne = 50,
                ).map { it.getPlaceId() }

        assertEquals(listOf(inRegion), pins)
        assertFalse(otherRegion in pins)
    }

    @Test
    fun `핀 좌표는 넣은 값 그대로 돌아온다`() {
        val (lat, lng) = isolatedPoint()
        val place = fixtures.newPlace(latitude = lat, longitude = lng, reviewCount = 1)

        val pin =
            repository
                .findPins(
                    north = lat + 0.01,
                    south = lat - 0.01,
                    east = lng + 0.01,
                    west = lng - 0.01,
                    centerLat = null,
                    centerLng = null,
                    query = null,
                    queryCategoryCsv = "",
                    categoryId = null,
                    regionPrefix = null,
                    limitPlusOne = 50,
                ).single { it.getPlaceId() == place }

        // ST_Y/ST_X가 위도·경도 순서로 나오는지 — 뒤집혀도 쿼리는 통과한다
        assertEquals(lat, pin.getLatitude(), 1e-6)
        assertEquals(lng, pin.getLongitude(), 1e-6)
    }

    companion object {
        private val OFFSET =
            java.util.concurrent.atomic
                .AtomicInteger(0)
    }
}
