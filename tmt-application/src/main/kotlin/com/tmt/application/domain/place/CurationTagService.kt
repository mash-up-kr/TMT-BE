package com.tmt.application.domain.place

import com.tmt.application.port.input.CurationTagView
import com.tmt.application.port.input.GetCurationTagsUseCase
import org.springframework.stereotype.Service

/**
 * 큐레이션 칩 목록 (B §2-4). 값의 정본은 [CurationPresets]이고 테이블이 아니다 —
 * 칩은 검색 조건 프리셋이라 조건이 바뀌면 서버 배포로 함께 나가야 한다 (E12).
 */
@Service
class CurationTagService : GetCurationTagsUseCase {
    override fun get(): List<CurationTagView> =
        CurationPresets.BY_ID.map { (id, preset) -> CurationTagView(id, preset.label) }
}
