package com.tmt.input.http.mock

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class InMemoryStoreTest {
    private data class Tag(
        val id: String,
        val label: String,
    )

    private val store = InMemoryStore<Tag>(idPrefix = "tag")

    @Test
    fun `생성하면 접두사_순번 형식의 ID가 발급된다`() {
        val first = store.create { id -> Tag(id, "하나") }
        val second = store.create { id -> Tag(id, "둘") }

        assertEquals("tag_1", first.id)
        assertEquals("tag_2", second.id)
    }

    @Test
    fun `저장한 엔티티를 ID로 조회한다`() {
        val saved = store.create { id -> Tag(id, "조회") }

        assertEquals(saved, store.findById(saved.id))
        assertNull(store.findById("tag_999"))
    }

    @Test
    fun `전체 조회는 생성 순서를 유지한다`() {
        val labels = listOf("가", "나", "다", "라")
        labels.forEach { label -> store.create { id -> Tag(id, label) } }

        assertEquals(labels, store.findAll().map { it.label })
    }

    @Test
    fun `수정은 기존 엔티티가 있을 때만 반영된다`() {
        val saved = store.create { id -> Tag(id, "이전") }

        val updated = store.update(saved.id) { it.copy(label = "이후") }

        assertEquals("이후", updated?.label)
        assertEquals("이후", store.findById(saved.id)?.label)
        assertNull(store.update("tag_999") { it.copy(label = "없음") })
    }

    @Test
    fun `삭제하면 조회되지 않는다`() {
        val saved = store.create { id -> Tag(id, "삭제") }

        assertTrue(store.delete(saved.id))
        assertNull(store.findById(saved.id))
        assertFalse(store.delete(saved.id))
    }

    @Test
    fun `동시에 생성해도 ID가 중복되지 않는다`() {
        val threads = 10
        val perThread = 100
        val executor = Executors.newFixedThreadPool(threads)

        repeat(threads) {
            executor.submit {
                repeat(perThread) {
                    store.create { id -> Tag(id, "동시성") }
                }
            }
        }
        executor.shutdown()
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS))

        val all = store.findAll()
        assertEquals(threads * perThread, all.size)
        assertEquals(threads * perThread, all.map { it.id }.toSet().size)
    }
}
