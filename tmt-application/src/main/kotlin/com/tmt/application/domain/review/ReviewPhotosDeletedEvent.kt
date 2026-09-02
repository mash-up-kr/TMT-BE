package com.tmt.application.domain.review

import com.tmt.application.port.output.storage.MediaStoragePort
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionalEventListener

private val logger = KotlinLogging.logger {}

/** 리뷰 삭제가 커밋된 뒤 지울 S3 객체 (R6). */
data class ReviewPhotosDeletedEvent(
    val reviewId: Long,
    val s3Keys: List<String>,
)

/**
 * 커밋 후에만 돈다. 삭제 트랜잭션 안에서 S3를 부르면 외부 I/O가 트랜잭션을 잡고, 롤백돼도
 * 객체는 이미 사라져 살아 있는 리뷰의 사진이 깨진다.
 *
 * 여기서 실패하면 참조 없는 객체가 남는다 — 화면에는 보이지 않고 다음 정리로 걷어낼 수 있어
 * 삭제 자체를 되돌리는 것보다 낫다.
 */
@Component
class ReviewPhotosDeletedListener(
    private val mediaStoragePort: MediaStoragePort,
) {
    @TransactionalEventListener
    fun on(event: ReviewPhotosDeletedEvent) {
        runCatching { mediaStoragePort.delete(event.s3Keys) }
            .onFailure { e ->
                logger.warn(e) { "리뷰 사진 S3 삭제 실패 - reviewId=${event.reviewId}, keys=${event.s3Keys.size}" }
            }
    }
}
