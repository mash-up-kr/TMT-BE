package com.tmt.input.http.mock

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/** 찜은 저장 흐름과 무관한 별개 개념이다 (F1). 멱등 토글이라 add/remove 결과를 따지지 않는다 (F2). */
class MockFavoriteStore {
    // 좋아요 탭 정렬이 favoritedAt DESC라(J §3-3) 찜한 시각을 함께 들고 있다
    private val favoritesByUser = ConcurrentHashMap<Long, MutableMap<String, Instant>>()

    fun add(
        userId: Long,
        placeId: String,
        favoritedAt: Instant = Instant.now(),
    ) {
        favoritesByUser.computeIfAbsent(userId) { ConcurrentHashMap() }.putIfAbsent(placeId, favoritedAt)
    }

    fun remove(
        userId: Long,
        placeId: String,
    ) {
        favoritesByUser[userId]?.remove(placeId)
    }

    /** 비로그인(userId == null)이면 항상 false다 (B §1-1·§1-2). */
    fun isFavorite(
        userId: Long?,
        placeId: String,
    ): Boolean = userId != null && favoritesByUser[userId]?.containsKey(placeId) == true

    fun count(userId: Long): Int = favoritesByUser[userId]?.size ?: 0

    /** 찜한 최신순 placeId (J §3-3 · J-01 §6-5). */
    fun favoritePlaceIds(userId: Long): List<String> =
        favoritesByUser[userId]
            ?.entries
            ?.sortedWith(compareByDescending<Map.Entry<String, Instant>> { it.value }.thenByDescending { it.key })
            ?.map { it.key }
            ?: emptyList()
}
