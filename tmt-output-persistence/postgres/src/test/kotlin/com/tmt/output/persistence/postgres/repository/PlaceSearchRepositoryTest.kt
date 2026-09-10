package com.tmt.output.persistence.postgres.repository

import com.tmt.output.persistence.postgres.support.PersistenceFixtures
import com.tmt.output.persistence.postgres.support.PersistenceTest
import com.tmt.output.persistence.postgres.support.assertKeysetWalk
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

    /** 관용 술어 테스트가 같은 파라미터 묶음을 반복하지 않게 모았다 (TMT-413). */
    private fun searchRelevance(
        query: String,
        region: String,
        after: Pair<Int, Long>? = null,
        limitPlusOne: Int = 50,
    ) = repository.searchByRelevance(
        query = query,
        queryPattern = LikePatterns.contains(query),
        queryNoSpacePattern = LikePatterns.containsIgnoringSpaces(query),
        queryPrefixPattern = LikePatterns.startsWith(query),
        queryCategoryCsv = "",
        categoryId = null,
        regionPrefix = region,
        afterSortValue = after?.first,
        afterPlaceId = after?.second,
        viewerId = null,
        limitPlusOne = limitPlusOne,
    )

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
                queryNoSpacePattern = null,
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
            queryNoSpacePattern = null,
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
                queryNoSpacePattern = null,
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
                queryNoSpacePattern = LikePatterns.containsIgnoringSpaces("김밥천국"),
                queryPrefixPattern = LikePatterns.startsWith("김밥천국"),
                queryCategoryCsv = "",
                categoryId = null,
                regionPrefix = region,
                afterSortValue = null,
                afterPlaceId = null,
                viewerId = null,
                limitPlusOne = 50,
            )

        assertEquals(listOf(exact, partial), rows.map { it.getPlaceId() })
        // 이름 등급(8000) + 앞매칭(2000) + 유사도 만점(1000) — 커서에 부동소수가 실리지 않는다
        assertEquals(11000, rows.first().getSortValue())
        // 같은 등급·같은 앞매칭이라 유사도로만 갈린다
        assertTrue(rows.last().getSortValue() in 10000..10999)
        // 좌표 없는 경로라 거리는 계산하지 않는다
        assertNull(rows.first().getDistanceMeters())
    }

    @Test
    fun `띄어쓰기가 달라도 이름으로 찾는다 (TMT-413)`() {
        // 운영에서 `위드유 용산`이 `위드유용산카페`를 0건으로 놓쳤다 — ILIKE는 글자가 그대로 이어져야 걸린다
        val region = isolatedRegion()
        val target = fixtures.newPlace(name = "위드유용산카페", regionName = region)

        val rows = searchRelevance("위드유 용산", region)

        assertEquals(listOf(target), rows.map { it.getPlaceId() })
    }

    @Test
    fun `띄어쓰기만 다른 이름도 이름 등급을 받아 주소보다 앞이다 (TMT-413)`() {
        // 걸리기만 하고 점수를 못 받으면 정답이 최하위로 밀린다 — 등급 판정도 공백을 관용한다
        val region = isolatedRegion()
        val byName = fixtures.newPlace(name = "오한수우육면가", regionName = region)
        val byAddress =
            fixtures.newPlace(
                name = "이름에 없는 가게",
                roadAddress = "서울특별시 중구 오한수 우육면로 3",
                regionName = region,
            )

        val rows = searchRelevance("오한수 우육면", region)

        assertEquals(byName, rows.first().getPlaceId(), "띄어쓰기만 다른 이름이 주소 매칭보다 앞이다")
        assertTrue(rows.first().getSortValue() >= 8000, "이름 등급을 받는다")
        assertTrue(byAddress in rows.map { it.getPlaceId() })
        assertTrue(rows.last().getSortValue() < 8000, "주소로 걸린 건은 이름 등급 아래다")
    }

    @Test
    fun `어순이 어긋나도 유사도 술어로 찾는다 (TMT-413)`() {
        // `홍대 스타벅스`는 어느 부분 일치에도 안 걸린다. pg_trgm 유사도(%)가 잡는 자리다
        val region = isolatedRegion()
        val target = fixtures.newPlace(name = "스타벅스 홍대역", regionName = region)
        val unrelated = fixtures.newPlace(name = "전혀 다른 국밥집", regionName = region)

        val rows = searchRelevance("홍대 스타벅스", region)

        assertTrue(target in rows.map { it.getPlaceId() }, "유사도로 걸린다")
        assertFalse(unrelated in rows.map { it.getPlaceId() }, "무관한 매장은 임계값에서 걸러진다")
    }

    @Test
    fun `관용 술어로 걸린 결과도 커서로 중복·누락 없이 순회한다 (TMT-413)`() {
        val region = isolatedRegion()
        // 이름이 같은 매장 셋 — 점수가 같아 (sortValue, placeId) tie-breaker만 남는다
        repeat(3) { fixtures.newPlace(name = "위드유용산카페", regionName = region) }
        val expected = searchRelevance("위드유 용산", region).map { it.getPlaceId() }

        assertKeysetWalk(
            expected = expected,
            idOf = { row: PlaceSearchRepository.PlaceSearchRowView -> row.getPlaceId() },
            page = { after ->
                searchRelevance(
                    query = "위드유 용산",
                    region = region,
                    after = after?.let { it.getSortValue() to it.getPlaceId() },
                    limitPlusOne = 1,
                )
            },
        )
    }

    @Test
    fun `이름에 걸린 매장은 유사도가 낮아도 주소·카테고리보다 앞이다 (TMT-300)`() {
        val region = isolatedRegion()
        // `피자`는 이름 뒤에 붙어 유사도가 0.125 수준이다 — 등급이 없으면 주소 매칭에 밀린다
        val nameSuffix = fixtures.newPlace(name = "델리스피자", regionName = region, categoryId = null)
        val addressHit =
            fixtures.newPlace(
                name = "무관한가게",
                roadAddress = "서울특별시 중구 피자거리 3",
                regionName = region,
                categoryId = null,
            )
        val categoryHit = fixtures.newPlace(name = "이름무관", regionName = region, categoryId = "cat_fastfood")

        val rows =
            repository.searchByRelevance(
                query = "피자",
                queryPattern = LikePatterns.contains("피자"),
                queryNoSpacePattern = LikePatterns.containsIgnoringSpaces("피자"),
                queryPrefixPattern = LikePatterns.startsWith("피자"),
                queryCategoryCsv = "cat_fastfood",
                categoryId = null,
                regionPrefix = region,
                afterSortValue = null,
                afterPlaceId = null,
                viewerId = null,
                limitPlusOne = 50,
            )

        assertEquals(listOf(nameSuffix, addressHit, categoryHit), rows.map { it.getPlaceId() })
        // 등급 간격(4000)이 등급 안 최대치(2000+1000)보다 커서 경계가 겹치지 않는다
        val byId = rows.associate { it.getPlaceId() to it.getSortValue() }
        assertTrue(byId.getValue(nameSuffix) in 8000..8999, "이름 등급·앞매칭 없음")
        assertTrue(byId.getValue(addressHit) in 4000..4999, "주소 등급")
        assertTrue(byId.getValue(categoryHit) < 4000, "카테고리 등급")
    }

    @Test
    fun `같은 이름 등급 안에서는 앞매칭이 유사도를 이긴다 (TMT-300)`() {
        val region = isolatedRegion()
        // 유사도만 보면 짧은 `원조본죽`이 이긴다 — 앞매칭 가산점이 그것을 뒤집는다
        val prefixLongName = fixtures.newPlace(name = "본죽비빔밥카페", regionName = region)
        val suffixShortName = fixtures.newPlace(name = "원조본죽", regionName = region)

        val rows =
            repository.searchByRelevance(
                query = "본죽",
                queryPattern = LikePatterns.contains("본죽"),
                queryNoSpacePattern = LikePatterns.containsIgnoringSpaces("본죽"),
                queryPrefixPattern = LikePatterns.startsWith("본죽"),
                queryCategoryCsv = "",
                categoryId = null,
                regionPrefix = region,
                afterSortValue = null,
                afterPlaceId = null,
                viewerId = null,
                limitPlusOne = 50,
            )

        assertEquals(listOf(prefixLongName, suffixShortName), rows.map { it.getPlaceId() })
        val byId = rows.associate { it.getPlaceId() to it.getSortValue() }
        assertTrue(byId.getValue(prefixLongName) >= 10000, "앞매칭 가산점 2000이 붙는다")
        assertTrue(byId.getValue(suffixShortName) < 10000, "앞매칭이 아니면 가산점이 없다")
        // 가산점(2000)이 유사도 만점(1000)보다 크다는 것이 이 역전의 근거다
        assertTrue(byId.getValue(prefixLongName) > byId.getValue(suffixShortName))
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
            queryNoSpacePattern = null,
            queryPrefixPattern = null,
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
                    queryNoSpacePattern = LikePatterns.containsIgnoringSpaces(token),
                    queryPrefixPattern = LikePatterns.startsWith(token),
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
                    queryNoSpacePattern = LikePatterns.containsIgnoringSpaces("한식"),
                    queryPrefixPattern = LikePatterns.startsWith("한식"),
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
                    queryNoSpacePattern = null,
                    queryPrefixPattern = null,
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

    @Test
    fun `패턴이 없으면 검색어 조건이 아예 걸리지 않는다 — 널 가드의 정본은 패턴이다`() {
        val region = isolatedRegion()
        val korean = fixtures.newPlace(regionName = region, categoryId = "cat_korean")
        val japanese = fixtures.newPlace(regionName = region, categoryId = "cat_japanese")

        // 빈 검색어가 흘러들어온 상황 — query는 비어 있지만 LikePatterns.contains는 null을 준다.
        // :query로 가르면 "검색어 있음"이 되고 ILIKE NULL은 NULL이라 카테고리 갈래만 남는다 (TMT-335)
        val rows =
            repository.searchByRelevance(
                query = "",
                queryPattern = null,
                queryNoSpacePattern = null,
                queryPrefixPattern = null,
                queryCategoryCsv = "cat_korean",
                categoryId = null,
                regionPrefix = region,
                afterSortValue = null,
                afterPlaceId = null,
                viewerId = null,
                limitPlusOne = 10,
            )

        assertEquals(setOf(korean, japanese), rows.map { it.getPlaceId() }.toSet())
    }

    @Test
    fun `거리순도 같은 규칙이다`() {
        val region = isolatedRegion()
        val korean =
            fixtures.newPlace(regionName = region, categoryId = "cat_korean", latitude = BASE_LAT, longitude = BASE_LNG)
        val japanese =
            fixtures.newPlace(
                regionName = region,
                categoryId = "cat_japanese",
                latitude = BASE_LAT + 0.001,
                longitude = BASE_LNG,
            )

        val rows =
            repository.searchByDistance(
                lat = BASE_LAT,
                lng = BASE_LNG,
                radius = null,
                query = "",
                queryPattern = null,
                queryNoSpacePattern = null,
                queryCategoryCsv = "cat_korean",
                categoryId = null,
                regionPrefix = region,
                afterSortValue = null,
                afterPlaceId = null,
                viewerId = null,
                limitPlusOne = 10,
            )

        assertEquals(listOf(korean, japanese), rows.map { it.getPlaceId() })
    }

    @Test
    fun `검색어의 퍼센트는 와일드카드가 아니라 글자다 (TMT-296)`() {
        val region = isolatedRegion()
        val literal = fixtures.newPlace(name = "100% 수제버거", regionName = region)
        fixtures.newPlace(name = "김밥천국", regionName = region)

        val rows =
            repository.searchByRelevance(
                query = "100%",
                queryPattern = LikePatterns.contains("100%"),
                queryNoSpacePattern = LikePatterns.containsIgnoringSpaces("100%"),
                queryPrefixPattern = LikePatterns.startsWith("100%"),
                queryCategoryCsv = "",
                categoryId = null,
                regionPrefix = region,
                afterSortValue = null,
                afterPlaceId = null,
                viewerId = null,
                limitPlusOne = 10,
            )

        // 이스케이프가 없으면 `%`가 전 행을 잡아 김밥천국까지 나온다
        assertEquals(listOf(literal), rows.map { it.getPlaceId() })
    }

    private fun s3KeyOf(mediaAssetId: Long): String =
        jdbcTemplate.queryForObject("SELECT s3_key FROM media_asset WHERE id = ?", String::class.java, mediaAssetId)!!

    companion object {
        // 실데이터·다른 테스트와 안 겹치는 바다 위 좌표. 거리 술어의 절대값은 검증 대상이 아니다
        private const val BASE_LAT = 20.0
        private const val BASE_LNG = 130.0
    }
}
