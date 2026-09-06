package com.tmt.application.domain.media

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * s3_key → 공개 조회 URL (TMT-201: base-url + s3_key가 곧 조회 URL이다).
 *
 * base-url이 비면 **기동 단계에서 실패한다** — 빈 값이면 모든 이미지 URL이 조용히
 * `/{s3Key}` 상대경로로 나가고, 그 화면을 열어보기 전까지 아무도 모른다 (PR #80·#82 리뷰).
 * 정본은 SSM `/tmt-prod/media/base-url` → 배포 .env. 로컬은 application-local.yml.
 */
@Component
class MediaUrlResolver(
    @param:Value("\${tmt.media.base-url:}") private val baseUrl: String,
) {
    init {
        require(baseUrl.isNotBlank()) {
            "tmt.media.base-url이 비어 있다 — 로컬은 application-local.yml, 운영은 SSM /tmt-prod/media/base-url을 확인하라"
        }
    }

    fun urlOf(s3Key: String): String = "${baseUrl.trimEnd('/')}/$s3Key"
}
