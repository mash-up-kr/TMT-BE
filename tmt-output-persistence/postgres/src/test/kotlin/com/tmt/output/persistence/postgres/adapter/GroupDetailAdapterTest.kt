package com.tmt.output.persistence.postgres.adapter

import com.tmt.output.persistence.postgres.support.PersistenceTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 그룹 상세의 지역 태그·커버 (D_02 §3-1, TMT-221·222) — TMT-348 전수 커버.
 *
 * 커버는 `GroupCoverSql` 정본을 상세 쪽에서 쓰는 절반이다 (TMT-305). 탐색·홈은 1장,
 * 상세는 N장을 쓰는데 **정렬이 같아야** 같은 그룹이 화면마다 다른 사진으로 보이지 않는다 (G16).
 */
@Import(GroupDetailAdapter::class)
class GroupDetailAdapterTest : PersistenceTest() {
    @Autowired
    private lateinit var adapter: GroupDetailAdapter

    @Test
    fun `지역 태그는 id 순으로 나온다`() {
        val group = fixtures.newGroup(fixtures.newUser())
        fixtures.addRegionTag(group, "rt_mapo")
        fixtures.addRegionTag(group, "rt_gangnam")

        assertEquals(listOf("rt_gangnam", "rt_mapo"), adapter.findRegionTagIds(group))
    }

    @Test
    fun `지역 태그가 없으면 빈 목록이다`() {
        assertEquals(emptyList(), adapter.findRegionTagIds(fixtures.newGroup(fixtures.newUser())))
    }

    @Test
    fun `커버는 공유 리뷰 최신순이고 리뷰 안에서는 photo_order 순이다`() {
        // G16 — 탐색·홈이 쓰는 1장과 같은 정렬이어야 한다 (GroupCoverSql 정본)
        val owner = fixtures.newUser()
        val group = fixtures.newGroup(owner)
        val older = fixtures.newPublishedReview(fixtures.newPlace(), createdAt = t(1))
        val newer = fixtures.newPublishedReview(fixtures.newPlace(), createdAt = t(5))
        val olderPhoto = fixtures.newMediaAsset(older.userId)
        val newerSecond = fixtures.newMediaAsset(newer.userId)
        val newerFirst = fixtures.newMediaAsset(newer.userId)
        fixtures.attachPhoto(older.saveId, olderPhoto, photoOrder = 0)
        fixtures.attachPhoto(newer.saveId, newerSecond, photoOrder = 1)
        fixtures.attachPhoto(newer.saveId, newerFirst, photoOrder = 0)
        fixtures.shareReview(group, older.reviewId, older.userId)
        fixtures.shareReview(group, newer.reviewId, newer.userId)

        val keys = adapter.findCoverImages(group, 10).map { it.s3Key }

        assertEquals(listOf(s3KeyOf(newerFirst), s3KeyOf(newerSecond), s3KeyOf(olderPhoto)), keys)
    }

    @Test
    fun `커버는 limit만큼만 나온다`() {
        val owner = fixtures.newUser()
        val group = fixtures.newGroup(owner)
        repeat(3) {
            val r = fixtures.newPublishedReview(fixtures.newPlace(), createdAt = t(it.toLong()))
            fixtures.attachPhoto(r.saveId, fixtures.newMediaAsset(r.userId), photoOrder = 0)
            fixtures.shareReview(group, r.reviewId, r.userId)
        }

        assertEquals(2, adapter.findCoverImages(group, 2).size)
    }

    @Test
    fun `사진 없는 공유 리뷰는 커버에 기여하지 않는다`() {
        val owner = fixtures.newUser()
        val group = fixtures.newGroup(owner)
        val noPhoto = fixtures.newPublishedReview(fixtures.newPlace(), createdAt = t(9))
        val withPhoto = fixtures.newPublishedReview(fixtures.newPlace(), createdAt = t(1))
        val asset = fixtures.newMediaAsset(withPhoto.userId)
        fixtures.attachPhoto(withPhoto.saveId, asset, photoOrder = 0)
        fixtures.shareReview(group, noPhoto.reviewId, noPhoto.userId)
        fixtures.shareReview(group, withPhoto.reviewId, withPhoto.userId)

        val images = adapter.findCoverImages(group, 10)

        assertEquals(listOf(s3KeyOf(asset)), images.map { it.s3Key })
        assertEquals(withPhoto.reviewId, images.single().reviewId)
    }

    @Test
    fun `삭제된 리뷰의 사진은 커버에서 빠진다`() {
        val owner = fixtures.newUser()
        val group = fixtures.newGroup(owner)
        val deleted = fixtures.newPublishedReview(fixtures.newPlace(), createdAt = t(1), deletedAt = Instant.now())
        fixtures.attachPhoto(deleted.saveId, fixtures.newMediaAsset(deleted.userId), photoOrder = 0)
        fixtures.shareReview(group, deleted.reviewId, deleted.userId)

        assertTrue(adapter.findCoverImages(group, 10).isEmpty())
    }

    private fun t(seconds: Long): Instant = Instant.parse("2026-09-01T00:00:00Z").plusSeconds(seconds)

    private fun s3KeyOf(mediaAssetId: Long): String =
        jdbcTemplate.queryForObject(
            "SELECT s3_key FROM media_asset WHERE id = ?",
            String::class.java,
            mediaAssetId,
        )!!
}
