package com.tmt.application.port.input

/** 전 유저 활동량 랭킹 (TMT-436). */
fun interface GetUserRankingsUseCase {
    fun get(request: UserRankingsRequest): UserRankingsResult
}

/** 랭킹 정렬 축 (TMT-438). 화면의 탭 둘이 이 값만 바꿔 부른다. */
enum class UserRankingSort(
    val apiValue: String,
) {
    REVIEW_COUNT("reviewCount"),
    SHARED_REVIEW_COUNT("sharedReviewCount"),
    ;

    companion object {
        val DEFAULT = REVIEW_COUNT

        /** 목록 밖이면 null — 호출자가 VALIDATION_FAILED로 바꾼다. */
        fun fromApiValue(value: String?): UserRankingSort? =
            if (value == null) DEFAULT else entries.firstOrNull { it.apiValue == value }
    }
}

data class UserRankingsRequest(
    val sort: UserRankingSort,
    val after: UserRankingKey?,
    val limit: Int,
)

/**
 * (정렬값, userId) 내림차순. 정렬값의 의미는 요청의 sort가 정한다.
 * 마지막 키는 유일해야 한다 — userId가 tie-breaker다.
 */
data class UserRankingKey(
    val sortValue: Int,
    val userId: Long,
)

data class UserRankingsResult(
    val items: List<UserRankingView>,
    val hasNext: Boolean,
    /** 마지막 행의 정렬 키. 다음 페이지가 없으면 null이다. */
    val lastKey: UserRankingKey? = null,
)

data class UserRankingView(
    val userId: Long,
    val nickname: String,
    val profileImageUrl: String?,
    val reviewCount: Int,
    /** 그룹에 공유한 리뷰 수. 한 리뷰를 여러 그룹에 공유해도 1이다. */
    val sharedReviewCount: Int,
)
