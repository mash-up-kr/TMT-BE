package com.tmt.input.http.mock

import java.util.Collections
import java.util.concurrent.atomic.AtomicLong

/**
 * mock API 전용 인메모리 저장소 — 스키마 확정(TMT-96) 전까지 DB를 대신한다.
 * ID는 명세 v2의 표기(place_1, review_9)를 따라 `<접두사>_<순번>` 문자열로 발급하고,
 * 삽입 순서를 보존하는 맵을 그대로 ID로 키잉해 전체 조회가 생성 순서를 유지한다.
 */
class InMemoryStore<T : Any>(
    private val idPrefix: String,
) {
    private val sequence = AtomicLong()
    private val entities: MutableMap<String, T> = Collections.synchronizedMap(LinkedHashMap())

    fun create(build: (id: String) -> T): T {
        val id = "${idPrefix}_${sequence.incrementAndGet()}"
        return build(id).also { entities[id] = it }
    }

    fun findById(id: String): T? = entities[id]

    // synchronizedMap의 컬렉션 뷰 순회는 원자적이지 않아 맵 잠금 하에 복사한다
    fun findAll(): List<T> = synchronized(entities) { entities.values.toList() }

    fun update(
        id: String,
        mutate: (T) -> T,
    ): T? = entities.computeIfPresent(id) { _, current -> mutate(current) }

    fun delete(id: String): Boolean = entities.remove(id) != null

    fun clear() = entities.clear()
}
