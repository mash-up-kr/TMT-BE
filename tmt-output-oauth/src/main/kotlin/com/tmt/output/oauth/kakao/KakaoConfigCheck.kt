package com.tmt.output.oauth.kakao

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

/**
 * 기동 시점에 이미 아는 사실은 기동 시점에 알린다 (docs/LOGGING.md §3-3). 키가 없으면 로그인이
 * 통째로 막히는데, 지금까지는 **첫 사용자가 실패해야** 알 수 있었다.
 */
@Component
class KakaoConfigCheck(
    @param:Value("\${tmt.auth.kakao.rest-api-key:}") private val restApiKey: String,
    @param:Value("\${tmt.auth.kakao.client-secret:}") private val clientSecret: String,
) {
    init {
        val missing =
            buildList {
                if (restApiKey.isBlank()) add("tmt.auth.kakao.rest-api-key")
                if (clientSecret.isBlank()) add("tmt.auth.kakao.client-secret")
            }
        if (missing.isEmpty()) {
            logger.info { "카카오 로그인 설정 확인" }
        } else {
            logger.error { "카카오 키가 없다 - 로그인을 쓸 수 없다. ${missing.joinToString(", ")} 확인" }
        }
    }
}
