package com.tmt.application.port.input

import java.time.Instant

/** 가입 팝업 (H §2-1). preview는 참고값이고 가입이 조건을 다시 검증한다 (TX-3). */
fun interface GetJoinPreviewUseCase {
    fun get(
        groupId: Long,
        userId: Long,
    ): JoinPreviewView
}

/** 가입 (H §2-2). 티켓 소비·멤버십 생성·자동 공유가 한 트랜잭션이다 (TX-3). */
fun interface JoinGroupUseCase {
    fun join(command: JoinGroupCommand): JoinGroupResult
}

/** 탈퇴 (H §3-3). 이 그룹에 공유한 리뷰가 전부 내려가고 (G10) 티켓은 돌아오지 않는다 (T9). */
fun interface LeaveGroupUseCase {
    fun leave(
        groupId: Long,
        userId: Long,
    )
}

data class JoinPreviewView(
    val groupId: Long,
    val name: String,
    val imageUrl: String?,
    val availableTicketCount: Int,
    val requiredTicketCount: Int,
    val blockedReason: JoinBlockedReason?,
) {
    val joinable: Boolean get() = blockedReason == null
}

enum class JoinBlockedReason {
    /** 이미 가입한 그룹 — 티켓 부족보다 먼저 판정한다 (G8) */
    ALREADY_MEMBER,
    TICKET_REQUIRED,
}

/** @param sourceReviewIds 가입과 함께 공유할 내 리뷰. 비면 공유 없이 가입만 성립한다 (G9) */
data class JoinGroupCommand(
    val userId: Long,
    val groupId: Long,
    val sourceReviewIds: List<Long>,
)

data class JoinGroupResult(
    val groupId: Long,
    val joinedAt: Instant,
    val sharedReviewIds: List<Long>,
    val consumedCount: Int,
    /** 차감 후 잔여 — 화면이 다시 조회하지 않아도 되게 함께 내린다 */
    val availableCount: Int,
)
