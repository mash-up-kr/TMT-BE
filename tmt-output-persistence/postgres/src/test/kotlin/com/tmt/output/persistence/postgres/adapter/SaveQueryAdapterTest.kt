package com.tmt.output.persistence.postgres.adapter

import com.tmt.application.port.output.persistence.MySaveRow
import com.tmt.output.persistence.postgres.support.PersistenceTest
import com.tmt.output.persistence.postgres.support.assertKeysetWalk
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import java.sql.Timestamp
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 저장 읽기 네이티브 쿼리 (I §6-2 · G §5-1, TMT-225·226) — TMT-348 전수 커버.
 *
 * 이어쓰기 목록은 **리뷰가 되지 않은 저장만** 본다 (R8). 그 조건이 `NOT EXISTS`라
 * 리뷰를 붙였다 지웠다 하는 경계가 SQL에서만 드러난다.
 */
@Import(SaveQueryAdapter::class)
class SaveQueryAdapterTest : PersistenceTest() {
    @Autowired
    private lateinit var adapter: SaveQueryAdapter

    @Test
    fun `저장 상세는 매장 정보와 함께 나오고 리뷰 전이면 reviewId가 null이다`() {
        val user = fixtures.newUser()
        val place = fixtures.newPlace(name = "이어쓰기가게", roadAddress = "서울특별시 중구 세종대로 1")
        val save = fixtures.newSave(user, place, rating = 4, content = "쓰는 중")

        val row = assertNotNull(adapter.findSave(save))

        assertEquals(user, row.userId)
        assertNull(row.reviewId, "리뷰 전이면 null이다 (S3)")
        assertEquals("이어쓰기가게", row.placeName)
        assertEquals(4, row.rating)
    }

    @Test
    fun `리뷰가 된 저장은 reviewId가 채워진다`() {
        val published = fixtures.newPublishedReview(fixtures.newPlace())

        val row = assertNotNull(adapter.findSave(published.saveId))

        assertEquals(published.reviewId, row.reviewId)
    }

    @Test
    fun `없는 저장은 null이다`() {
        assertNull(adapter.findSave(-1L))
    }

    @Test
    fun `저장 상세에 AI 요약이 붙는다`() {
        val published = fixtures.newPublishedReview(fixtures.newPlace())
        fixtures.addSummary(published.reviewId, pros = "친절함", cons = "웨이팅")

        val row = assertNotNull(adapter.findSave(published.saveId))

        assertEquals("친절함", row.aiSummaryPros)
        assertEquals("웨이팅", row.aiSummaryCons)
    }

    @Test
    fun `이어쓰기 목록에는 리뷰가 된 저장이 안 나온다`() {
        // R8 — 미완성 저장만 이어쓰기 대상이다
        val user = fixtures.newUser()
        val draft = fixtures.newSave(user, fixtures.newPlace())
        val published = fixtures.newPublishedReview(fixtures.newPlace(), user)

        val ids = adapter.findMySaveRows(user, null, null, 20).rows.map { it.saveId }

        assertEquals(listOf(draft), ids)
        assertFalse(published.saveId in ids)
    }

    @Test
    fun `삭제된 리뷰의 저장은 이어쓰기 목록으로 돌아온다`() {
        // NOT EXISTS가 deleted_at을 보는지 — 리뷰를 지우면 그 저장은 다시 미완성이다
        val user = fixtures.newUser()
        val published = fixtures.newPublishedReview(fixtures.newPlace(), user, deletedAt = Instant.now())

        val ids = adapter.findMySaveRows(user, null, null, 20).rows.map { it.saveId }

        assertEquals(listOf(published.saveId), ids)
    }

    @Test
    fun `이어쓰기 목록 썸네일은 첫 사진이고 사진이 없으면 null이다`() {
        val user = fixtures.newUser()
        val withPhoto = fixtures.newSave(user, fixtures.newPlace())
        val without = fixtures.newSave(user, fixtures.newPlace())
        val first = fixtures.newMediaAsset(user)
        fixtures.attachPhoto(withPhoto, fixtures.newMediaAsset(user), photoOrder = 1)
        fixtures.attachPhoto(withPhoto, first, photoOrder = 0)

        val byId = adapter.findMySaveRows(user, null, null, 20).rows.associateBy { it.saveId }

        assertEquals(s3KeyOf(first), byId.getValue(withPhoto).thumbnailS3Key)
        assertNull(byId.getValue(without).thumbnailS3Key)
    }

    @Test
    fun `이어쓰기 커서가 동률 경계에서 중복도 누락도 없다`() {
        // updated_at을 같은 값으로 맞춰 tie-breaker(save_id)만으로 갈리게 한다
        val user = fixtures.newUser()
        val ids = (1..4).map { fixtures.newSave(user, fixtures.newPlace()) }
        val sameTime = Instant.parse("2026-09-01T00:00:00Z")
        ids.forEach {
            jdbcTemplate.update("UPDATE save SET updated_at = ? WHERE id = ?", Timestamp.from(sameTime), it)
        }

        assertKeysetWalk<MySaveRow>(
            expected = ids.sortedDescending(),
            idOf = { it.saveId },
        ) { after ->
            adapter.findMySaveRows(user, after?.updatedAt, after?.saveId, 1).rows
        }
    }

    @Test
    fun `hasNext는 limit을 넘겼을 때만 참이다`() {
        val user = fixtures.newUser()
        repeat(2) { fixtures.newSave(user, fixtures.newPlace()) }

        assertTrue(adapter.findMySaveRows(user, null, null, 1).hasNext)
        assertFalse(adapter.findMySaveRows(user, null, null, 2).hasNext)
    }

    private fun s3KeyOf(mediaAssetId: Long): String =
        jdbcTemplate.queryForObject(
            "SELECT s3_key FROM media_asset WHERE id = ?",
            String::class.java,
            mediaAssetId,
        )!!
}
