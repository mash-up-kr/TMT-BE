package com.tmt.input.http.mock

import java.util.concurrent.ConcurrentHashMap

/** 찜은 저장 흐름과 무관한 별개 개념이다 (F1). 멱등 토글이라 add/remove 결과를 따지지 않는다 (F2). */
class MockFavoriteStore {
    private val favoritesByUser = ConcurrentHashMap<Long, MutableSet<String>>()

    fun add(
        userId: Long,
        placeId: String,
    ) {
        favoritesByUser.computeIfAbsent(userId) { ConcurrentHashMap.newKeySet() }.add(placeId)
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
    ): Boolean = userId != null && favoritesByUser[userId]?.contains(placeId) == true
}
