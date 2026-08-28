package com.tmt.output.storage.s3

import com.tmt.application.port.output.storage.MediaStoragePort
import com.tmt.application.port.output.storage.PresignedUpload
import org.springframework.beans.factory.DisposableBean
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.Delete
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest
import software.amazon.awssdk.services.s3.model.ObjectIdentifier
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest
import java.time.Duration

/**
 * presigned URL 발급·객체 삭제 (TMT-201 버킷). 자격증명은 기본 체인이다 —
 * 운영은 인스턴스 역할(media-write: PutObject·DeleteObject), 로컬은 첫 발급 시점에야
 * 자격증명을 찾으므로 AWS 설정 없이도 기동은 된다.
 *
 * region을 프로퍼티로 고정하는 이유: 기본 체인의 region 탐색은 빈 생성 시점에 돌아서,
 * region 설정이 없는 로컬에서 기동 자체가 막힌다.
 */
@Component
class S3MediaStorageAdapter(
    @param:Value("\${tmt.media.bucket:}") private val bucket: String,
    @param:Value("\${tmt.media.region:ap-northeast-2}") region: String,
    @param:Value("\${tmt.media.upload-url-ttl:PT15M}") private val uploadUrlTtl: Duration,
) : MediaStoragePort,
    DisposableBean {
    private val presigner: S3Presigner = S3Presigner.builder().region(Region.of(region)).build()
    private val s3Client: S3Client = S3Client.builder().region(Region.of(region)).build()

    override fun presignPut(
        s3Key: String,
        contentType: String,
    ): PresignedUpload {
        val objectRequest =
            PutObjectRequest
                .builder()
                .bucket(bucket)
                .key(s3Key)
                .contentType(contentType)
                .build()
        val presigned =
            presigner.presignPutObject(
                PutObjectPresignRequest
                    .builder()
                    .signatureDuration(uploadUrlTtl)
                    .putObjectRequest(objectRequest)
                    .build(),
            )
        return PresignedUpload(url = presigned.url().toString(), expiresAt = presigned.expiration())
    }

    override fun delete(s3Keys: Collection<String>) {
        if (s3Keys.isEmpty()) return
        // DeleteObjects는 요청당 1,000개 제한
        s3Keys.chunked(1_000).forEach { chunk ->
            s3Client.deleteObjects(
                DeleteObjectsRequest
                    .builder()
                    .bucket(bucket)
                    .delete(
                        Delete
                            .builder()
                            .objects(chunk.map { ObjectIdentifier.builder().key(it).build() })
                            .build(),
                    ).build(),
            )
        }
    }

    override fun destroy() {
        presigner.close()
        s3Client.close()
    }
}
