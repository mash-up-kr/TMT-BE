package com.tmt.application.port.input

/**
 * 큐레이션 칩 운영 (E12, TMT-421). 읽기 전용 공개 API([GetCurationTagsUseCase])와 달리
 * **비활성 칩도 보여주고** 값을 바꾼다 — 운영 화면이 내린 칩을 다시 올릴 수 있어야 한다.
 *
 * 어드민 판정은 입력 어댑터가 경로(`/v1/admin/**`)로 막는다. 여기까지 들어온 호출은
 * 이미 어드민이다 — 사용자별 소유권 검증이 없는 이유다.
 */
interface ListCurationTagsForAdminUseCase {
    /** display_order 순. 비활성 칩도 포함한다 */
    fun list(): List<CurationTagAdminView>
}

interface CreateCurationTagUseCase {
    fun create(command: CurationTagCreateCommand): CurationTagAdminView
}

interface UpdateCurationTagUseCase {
    /** 삭제는 없다 — [CurationTagUpdateCommand.active]를 false로 내린다 (D4) */
    fun update(
        curationTagId: String,
        command: CurationTagUpdateCommand,
    ): CurationTagAdminView
}

interface GetCurationTagPlacesUseCase {
    /** pin_order 순. 운영 화면이 현재 목록을 알아야 전체 교체를 안전하게 보낼 수 있다 */
    fun get(curationTagId: String): List<CurationTagPlaceView>
}

interface ReplaceCurationTagPlacesUseCase {
    /**
     * 보낸 순서가 그대로 `pin_order`가 되고, 빠진 매장은 목록에서 내려간다.
     * 그룹 리뷰 공유(G14)와 같은 전체 교체라 멱등이다.
     */
    fun replace(
        curationTagId: String,
        placeIds: List<Long>,
    ): List<CurationTagPlaceView>
}

data class CurationTagCreateCommand(
    /** API의 `curationTagId`가 그대로 PK다 — 서버가 부여하지 않고 이후 바꿀 수 없다 */
    val curationTagId: String,
    val label: String,
    val displayOrder: Int,
)

/** null인 필드는 건드리지 않는다 — 운영 화면이 한 필드만 바꾸는 경우가 흔하다 */
data class CurationTagUpdateCommand(
    val label: String? = null,
    val displayOrder: Int? = null,
    val active: Boolean? = null,
)

data class CurationTagAdminView(
    val curationTagId: String,
    val label: String,
    val displayOrder: Int,
    val active: Boolean,
    /** 칩에 지정된 매장 수. 0이면 칩을 눌러도 결과가 비어 운영이 알아야 한다 */
    val placeCount: Int,
)

data class CurationTagPlaceView(
    val placeId: Long,
    val placeName: String,
    val roadAddress: String,
    val pinOrder: Int,
)
