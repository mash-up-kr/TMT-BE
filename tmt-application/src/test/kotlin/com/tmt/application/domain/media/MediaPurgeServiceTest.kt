package com.tmt.application.domain.media

import com.tmt.application.port.output.storage.MediaStoragePort
import com.tmt.application.port.output.storage.PresignedUpload
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class MediaPurgeServiceTest {
    private val port = FakeMediaAssetPort()
    private val deletedKeys = mutableListOf<String>()
    private val storage =
        object : MediaStoragePort {
            override fun presignPut(
                s3Key: String,
                contentType: String,
                contentLength: Long,
            ): PresignedUpload = throw UnsupportedOperationException()

            override fun delete(s3Keys: Collection<String>) {
                deletedKeys += s3Keys
            }
        }
    private val service = MediaPurgeService(port, storage, stagedTtl = Duration.ofDays(1))

    /** TTL 밖 = 어제 이전에 만들어진 것. 오늘 만든 것은 아직 작성 중일 수 있다. */
    private val old = Instant.now().minus(Duration.ofDays(3))
    private val fresh = Instant.now()

    @Test
    fun `TTL 지난 STAGED는 S3 객체와 행이 함께 사라진다 (M4)`() {
        val id = port.seed(attached = false, createdAt = old)

        assertEquals(1, service.purgeExpired())
        assertTrue(deletedKeys.contains("review/$id.jpg"))
        assertTrue(!port.exists(id))
    }

    @Test
    fun `ATTACHED는 아무리 오래돼도 지우지 않는다`() {
        val id = port.seed(attached = true, createdAt = old)

        assertEquals(0, service.purgeExpired())
        assertTrue(deletedKeys.isEmpty())
        assertTrue(port.exists(id))
    }

    @Test
    fun `TTL 안쪽 STAGED는 작성 중일 수 있으므로 남긴다`() {
        val id = port.seed(attached = false, createdAt = fresh)

        assertEquals(0, service.purgeExpired())
        assertTrue(port.exists(id))
    }

    @Test
    fun `지울 것이 없으면 스토리지를 부르지 않는다`() {
        assertEquals(0, service.purgeExpired())
        assertTrue(deletedKeys.isEmpty())
    }
}
