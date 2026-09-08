package com.tmt.output.address.juso

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

/**
 * 기동 시점에 이미 아는 사실은 기동 시점에 알린다 (docs/LOGGING.md §3-3). 승인키가 없으면
 * 주소 검색·좌표 조회가 막히는데, 지금까지는 **첫 사용자가 실패해야** 알 수 있었다.
 *
 * 검색과 좌표의 승인키가 서로 다르다. 하나만 비어도 그 기능은 통째로 불가하다.
 */
@Component
class JusoConfigCheck(
    @param:Value("\${tmt.address.juso.search-key:}") private val searchKey: String,
    @param:Value("\${tmt.address.juso.coord-key:}") private val coordKey: String,
) {
    init {
        val missing =
            buildList {
                if (searchKey.isBlank()) add("tmt.address.juso.search-key")
                if (coordKey.isBlank()) add("tmt.address.juso.coord-key")
            }
        if (missing.isEmpty()) {
            logger.info { "juso 주소 API 설정 확인" }
        } else {
            logger.error { "juso 승인키가 없다 - 주소 검색·매장 등록을 쓸 수 없다. ${missing.joinToString(", ")} 확인" }
        }
    }
}
