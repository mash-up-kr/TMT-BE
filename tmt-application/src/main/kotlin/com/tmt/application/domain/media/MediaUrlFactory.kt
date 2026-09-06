package com.tmt.application.domain.media

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/** 사진 조회 URL — `base-url/s3_key` (공개 읽기 버킷, TMT-201). */
@Component
class MediaUrlFactory(
    @param:Value("\${tmt.media.base-url:}") private val baseUrl: String,
) {
    fun of(s3Key: String): String = "${baseUrl.trimEnd('/')}/$s3Key"
}
