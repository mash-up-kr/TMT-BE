package com.tmt.application.port.input

/** 요약 없는 리뷰를 찾아 LLM으로 채운다 (TMT-232). 이벤트 유실 대비 배치가 이걸 주기 실행한다. */
interface SummarizePendingReviewsUseCase {
    /** 요약을 채운 리뷰 수를 돌려준다. */
    fun summarizePending(): Int
}
