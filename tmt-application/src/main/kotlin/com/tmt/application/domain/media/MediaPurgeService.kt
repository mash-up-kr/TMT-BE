package com.tmt.application.domain.media

import com.tmt.application.port.input.PurgeStagedMediaUseCase
import com.tmt.application.port.output.persistence.MediaAssetPort
import com.tmt.application.port.output.storage.MediaStoragePort
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant

private val logger = KotlinLogging.logger {}

/**
 * 작성 중 이탈로 남은 STAGED 사진을 걷어낸다 (M4). ATTACHED는 절대 건드리지 않는다 —
 * 조회 자체가 STAGED만 가져온다. S3 객체를 먼저 지우고 행을 지운다: 반대 순서에서
 * 중간에 죽으면 행 없는 고아 객체가 남아 다시는 찾지 못한다.
 */
@Service
class MediaPurgeService(
    private val mediaAssetPort: MediaAssetPort,
    private val mediaStoragePort: MediaStoragePort,
    @param:Value("\${tmt.media.staged-ttl:P1D}") private val stagedTtl: Duration,
) : PurgeStagedMediaUseCase {
    override fun purgeExpired(): Int {
        val expired = mediaAssetPort.findStagedCreatedBefore(Instant.now().minus(stagedTtl))
        if (expired.isEmpty()) return 0

        mediaStoragePort.delete(expired.map { it.s3Key })
        val deleted = mediaAssetPort.deleteByIds(expired.map { it.id })
        logger.info { "STAGED 미디어 TTL 정리 - deleted=$deleted" }
        return deleted
    }

    @Scheduled(cron = "\${tmt.media.purge-cron:0 40 4 * * *}", zone = "Asia/Seoul")
    fun purgeOnSchedule() {
        purgeExpired()
    }
}
