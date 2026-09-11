package com.tmt.application.domain.place

import com.tmt.application.port.input.CurationTagView
import com.tmt.application.port.input.GetCurationTagsUseCase
import com.tmt.application.port.output.persistence.CurationTagPort
import org.springframework.stereotype.Service

/**
 * 큐레이션 칩 목록 (B §2-4). 값의 정본은 `curation_tag` 테이블이다 (E12) —
 * 칩이 조건 프리셋이 아니라 운영이 고른 매장 목록이 되면서 상수에서 옮겨왔다
 * (질문 36, V9).
 */
@Service
class CurationTagService(
    private val curationTagPort: CurationTagPort,
) : GetCurationTagsUseCase {
    override fun get(): List<CurationTagView> = curationTagPort.findActiveTags().map { CurationTagView(it.curationTagId, it.label) }
}
