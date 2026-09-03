package com.tmt.output.persistence.postgres.repository

import com.tmt.output.persistence.postgres.support.PersistenceFixtures
import com.tmt.output.persistence.postgres.support.PersistenceTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant

/**
 * 매장 검색 네이티브 쿼리 (TMT-195).
 *
 * `searchByRelevance`에는 공간 술어가 없어 검색어를 비우면 DB의 모든 매장이 후보가 된다.
 * 테스트마다 유일한 `region_name`을 주고 `regionPrefix`로 후보를 자기 매장으로 좁힌다 —
 * 이 좁히기 없이는 다른 테스트가 만든 매장이 결과에 섞인다.
 */
class PlaceSearchRepositoryTest : PersistenceTest() {
    @Autowired
    private lateinit var repository: PlaceSearchRepository

    private fun isolatedRegion() = "테스트권역${PersistenceFixtures.nextSequence()}"

    @Test
    fun `거리순은 반올림 미터가 앞자리고 반경 밖은 빠진다`() {
        val region = isolatedRegion()
        val lat = BASE_LAT
        val lng = BASE_LNG
        val near = fixtures.newPlace(regionName = region, latitude = lat + 0.001, longitude = lng)
        val mid = fixtures.newPlace(regionName = region, latitude = lat + 0.002, longitude = lng)
        val outOfRadius = fixtures.newPlace(regionName = region, latitude = lat + 0.05, longitude = lng)

        val rows =
            repository.searchByDistance(
                lat = lat,
                lng = lng,
                radius = 1000,
                query = null,
                queryPattern = null,
                queryCategoryCsv = "",
                categoryId = null,
                regionPrefix = region,
                afterSortValue = null,
                afterPlaceId = null,
                viewerId = null,
                limitPlusOne = 50,
            )

        assertEquals(listOf(near, mid), rows.map { it.getPlaceId() })
        assertFalse(outOfRadius in rows.map { it.getPlaceId() })
        // sortValue는 커서에 실리는 값이라 distanceMeters와 같아야 한다
        assertEquals(rows.map { it.getDistanceMeters() }, rows.map { it.getSortValue() })
        assertTrue(rows.first().getSortValue() < rows.last().getSortValue())
    }

    @Test
    fun `거리순 커서는 오름차순 경계에서 겹치지도 빠뜨리지도 않는다`() {
        val region = isolatedRegion()
        val lat = BASE_LAT
        val lng = BASE_LNG
        // 같은 좌표 셋 — 거리가 같아 (distance, id) 행 비교의 tie-breaker만 남는다
        val ids = (1..3).map { fixtures.newPlace(regionName = region, latitude = lat, longitude = lng) }.sorted()

        fun page(
            afterSortValue: Int?,
            afterPlaceId: Long?,
        ) = repository.searchByDistance(
            lat = lat,
            lng = lng,
            radius = 1000,
            query = null,
            queryPattern = null,
            queryCategoryCsv = "",
            categoryId = null,
            regionPrefix = region,
            afterSortValue = afterSortValue,
            afterPlaceId = afterPlaceId,
            viewerId = null,
            limitPlusOne = 2,
        )

        val first = page(null, null)
        assertEquals(ids.take(2), first.map { it.getPlaceId() })

        val second = page(first.last().getSortValue(), first.last().getPlaceId())
        assertEquals(ids.drop(2), second.map { it.getPlaceId() })
    }

    @Test
    fun `반경이 없으면 거리 제한 없이 거리순으로만 정렬한다`() {
        val region = isolatedRegion()
        val lat = BASE_LAT
        val lng = BASE_LNG
        val near = fixtures.newPlace(regionName = region, latitude = lat + 0.001, longitude = lng)
        // 반경을 줬다면 잘렸을 거리 — radius가 null이면 후보에 남는다
        val far = fixtures.newPlace(regionName = region, latitude = lat + 0.5, longitude = lng)

        val rows =
            repository.searchByDistance(
                lat = lat,
                lng = lng,
                radius = null,
                query = null,
                queryPattern = null,
                queryCategoryCsv = "",
                categoryId = null,
                regionPrefix = region,
                afterSortValue = null,
                afterPlaceId = null,
                viewerId = null,
                limitPlusOne = 50,
            )

        assertEquals(listOf(near, far), rows.map { it.getPlaceId() })
        assertTrue(rows.last().getSortValue() > 50_000, "반경 밖 매장이 잘리지 않아야 한다")
    }

    @Test
    fun `유사도순은 이름이 가까운 순이고 점수는 정수다`() {
        val region = isolatedRegion()
        val exact = fixtures.newPlace(name = "김밥천국", regionName = region)
        val partial = fixtures.newPlace(name = "김밥천국 2호점 분식", regionName = region)

        val rows =
            repository.searchByRelevance(
                query = "김밥천국",
                queryPattern = LikePatterns.contains("김밥천국"),
                queryCategoryCsv = "",
                categoryId = null,
                regionPrefix = region,
                afterSortValue = null,
                afterPlaceId = null,
                viewerId = null,
                limitPlusOne = 50,
            )

        assertEquals(listOf(exact, partial), rows.map { it.getPlaceId() })
        // similarity × 1000 반올림 — 커서에 부동소수가 실리지 않는 근거다
        assertEquals(1000, rows.first().getSortValue())
        assertTrue(rows.last().getSortValue() < 1000)
        // 좌표 없는 경로라 거리는 계산하지 않는다
        assertNull(rows.first().getDistanceMeters())
    }

    @Test
    fun `유사도순 커서는 내림차순 경계에서 겹치지 않는다`() {
        val region = isolatedRegion()
        // 검색어가 없으면 점수가 전부 0이라 사실상 id DESC 한 축이 된다
        val ids = (1..3).map { fixtures.newPlace(regionName = region) }.sortedDescending()

        fun page(
            afterSortValue: Int?,
            afterPlaceId: Long?,
        ) = repository.searchByRelevance(
            query = null,
            queryPattern = null,
            queryCategoryCsv = "",
            categoryId = null,
            regionPrefix = region,
            afterSortValue = afterSortValue,
            afterPlaceId = afterPlaceId,
            viewerId = null,
            limitPlusOne = 2,
        )

        val first = page(null, null)
        assertEquals(ids.take(2), first.map { it.getPlaceId() })
        assertEquals(0, first.first().getSortValue())

        val second = page(first.last().getSortValue(), first.last().getPlaceId())
        assertEquals(ids.drop(2), second.map { it.getPlaceId() })
    }

    @Test
    fun `카테고리 칩은 검색어와 AND로 걸린다`() {
        val region = isolatedRegion()
        val token = "칩${PersistenceFixtures.nextSequence()}"
        val match = fixtures.newPlace(name = "$token 한식", regionName = region, categoryId = "cat_korean")
        val otherCategory = fixtures.newPlace(name = "$token 일식", regionName = region, categoryId = "japanese")

        val rows =
            repository
                .searchByRelevance(
                    query = token,
                    queryPattern = LikePatterns.contains(token),
                    queryCategoryCsv = "",
                    categoryId = "cat_korean",
                    regionPrefix = region,
                    afterSortValue = null,
                    afterPlaceId = null,
                    viewerId = null,
                    limitPlusOne = 50,
                ).map { it.getPlaceId() }

        assertEquals(listOf(match), rows)
        assertFalse(otherCategory in rows)
    }

    @Test
    fun `검색어가 카테고리 라벨이면 CSV로 넘어온 코드로도 잡힌다`() {
        val region = isolatedRegion()
        // 이름·주소에는 없고 카테고리만 맞는 매장 — 서비스가 라벨을 id로 바꿔 CSV로 넘긴 경우다
        val byCategory = fixtures.newPlace(name = "이름무관", regionName = region, categoryId = "cat_korean")
        val other = fixtures.newPlace(name = "이름무관", regionName = region, categoryId = "japanese")

        val rows =
            repository
                .searchByRelevance(
                    query = "한식",
                    queryPattern = LikePatterns.contains("한식"),
                    queryCategoryCsv = "cat_korean",
                    categoryId = null,
                    regionPrefix = region,
                    afterSortValue = null,
                    afterPlaceId = null,
                    viewerId = null,
                    limitPlusOne = 50,
                ).map { it.getPlaceId() }

        assertEquals(listOf(byCategory), rows)
        assertFalse(other in rows)
    }

    @Test
    fun `찜은 보는 사람 기준이다`() {
        val region = isolatedRegion()
        val place = fixtures.newPlace(regionName = region)
        val viewer = fixtures.newUser()
        val stranger = fixtures.newUser()
        fixtures.deleteFavorite(viewer, place)
        fixtures.addFavorite(viewer, place)

        fun favoriteFor(userId: Long?) =
            repository
                .searchByRelevance(
                    query = null,
                    queryPattern = null,
                    queryCategoryCsv = "",
                    categoryId = null,
                    regionPrefix = region,
                    afterSortValue = null,
                    afterPlaceId = null,
                    viewerId = userId,
                    limitPlusOne = 10,
                ).single()
                .getFavorite()

        assertTrue(favoriteFor(viewer))
        assertFalse(favoriteFor(stranger))
        assertFalse(favoriteFor(null))
    }

    @Test
    fun `대표 사진은 매장당 한 장이고 최신 리뷰의 첫 장이다`() {
        val place = fixtures.newPlace()
        val old = fixtures.newPublishedReview(place, createdAt = Instant.now().minusSeconds(600))
        val recent = fixtures.newPublishedReview(place, createdAt = Instant.now())
        fixtures.attachPhoto(old.saveId, fixtures.newMediaAsset(old.userId), photoOrder = 0)
        // 최신 리뷰의 두 번째 장을 먼저 넣어도 photo_order가 앞장을 고른다
        val second = fixtures.newMediaAsset(recent.userId)
        val first = fixtures.newMediaAsset(recent.userId)
        fixtures.attachPhoto(recent.saveId, second, photoOrder = 1)
        fixtures.attachPhoto(recent.saveId, first, photoOrder = 0)

        val rows = repository.findLatestPhotoRows(listOf(place))

        assertEquals(1, rows.size)
        assertEquals(place, rows.single().getPlaceId())
        assertEquals(s3KeyOf(first), rows.single().getS3Key())
    }

    @Test
    fun `삭제된 리뷰의 사진은 대표 사진이 되지 않는다`() {
        val place = fixtures.newPlace()
        val alive = fixtures.newPublishedReview(place, createdAt = Instant.now().minusSeconds(600))
        val deleted = fixtures.newPublishedReview(place, createdAt = Instant.now(), deletedAt = Instant.now())
        val aliveAsset = fixtures.newMediaAsset(alive.userId)
        fixtures.attachPhoto(alive.saveId, aliveAsset, photoOrder = 0)
        fixtures.attachPhoto(deleted.saveId, fixtures.newMediaAsset(deleted.userId), photoOrder = 0)

        val rows = repository.findLatestPhotoRows(listOf(place))

        assertEquals(s3KeyOf(aliveAsset), rows.single().getS3Key())
    }

    private fun s3KeyOf(mediaAssetId: Long): String =
        jdbcTemplate.queryForObject("SELECT s3_key FROM media_asset WHERE id = ?", String::class.java, mediaAssetId)!!

    companion object {
        // 실데이터·다른 테스트와 안 겹치는 바다 위 좌표. 거리 술어의 절대값은 검증 대상이 아니다
        private const val BASE_LAT = 20.0
        private const val BASE_LNG = 130.0
    }
}
