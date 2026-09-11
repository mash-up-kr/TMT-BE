package com.tmt.input.http.controller

import com.tmt.application.port.input.CreateCurationTagUseCase
import com.tmt.application.port.input.CurationTagAdminView
import com.tmt.application.port.input.CurationTagCreateCommand
import com.tmt.application.port.input.CurationTagPlaceView
import com.tmt.application.port.input.CurationTagUpdateCommand
import com.tmt.application.port.input.GetCurationTagPlacesUseCase
import com.tmt.application.port.input.ListCurationTagsForAdminUseCase
import com.tmt.application.port.input.ReplaceCurationTagPlacesUseCase
import com.tmt.application.port.input.UpdateCurationTagUseCase
import com.tmt.common.exception.ErrorCode
import com.tmt.input.http.auth.UserId
import com.tmt.input.http.config.ApiErrorCodes
import com.tmt.input.http.controller.dto.response.ItemsResponse
import com.tmt.input.http.controller.dto.response.PublicIds
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * 큐레이션 칩 운영 (TMT-421). **FE가 쓰는 API가 아니다** — 운영이 칩과 매장 목록을
 * 배포 없이 고치는 자리다. 공개 목록은 `GET /v1/curation-tags`가 그대로 담당한다.
 *
 * 인증·인가는 컨트롤러가 아니라 경로로 걸린다 — `/v1/admin/**`은
 * [AdminOnlyInterceptor][com.tmt.input.http.auth.AdminOnlyInterceptor]가 막는다.
 * `@UserId`를 받는 이유는 인가가 아니라 **누가 바꿨는지 로그에 남기기 위한 것**이다.
 */
@Tag(name = "큐레이션 운영 (admin)", description = "운영 전용 — 칩·매장 목록 편집. 허용 목록(tmt.admin.user-ids)에 없으면 403")
@RestController
@RequestMapping("/v1/admin/curation-tags")
class CurationTagAdminController(
    private val listCurationTagsForAdminUseCase: ListCurationTagsForAdminUseCase,
    private val createCurationTagUseCase: CreateCurationTagUseCase,
    private val updateCurationTagUseCase: UpdateCurationTagUseCase,
    private val getCurationTagPlacesUseCase: GetCurationTagPlacesUseCase,
    private val replaceCurationTagPlacesUseCase: ReplaceCurationTagPlacesUseCase,
) {
    @Operation(summary = "칩 목록 (운영)", description = "비활성 칩도 포함한다. 공개 목록과 달리 내린 칩을 다시 올릴 수 있어야 한다.")
    @ApiErrorCodes(ErrorCode.ADMIN_REQUIRED)
    @GetMapping
    fun listTags(
        @UserId userId: Long,
    ): ItemsResponse<CurationTagAdminResponse> = ItemsResponse(listCurationTagsForAdminUseCase.list().map(::toResponse))

    @Operation(
        summary = "칩 생성",
        description = "curationTagId는 서버가 부여하지 않는다 — 검색·핀 파라미터로 그대로 쓰이는 값이라 입력받고 이후 바꿀 수 없다.",
    )
    @ApiErrorCodes(ErrorCode.ADMIN_REQUIRED, ErrorCode.VALIDATION_FAILED, ErrorCode.CURATION_TAG_DUPLICATED)
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    fun createTag(
        @UserId userId: Long,
        @RequestBody request: CreateCurationTagRequest,
    ): CurationTagAdminResponse =
        toResponse(
            createCurationTagUseCase.create(
                CurationTagCreateCommand(
                    curationTagId = request.curationTagId,
                    label = request.label,
                    displayOrder = request.displayOrder,
                ),
            ),
        )

    @Operation(summary = "칩 수정", description = "보낸 필드만 바꾼다. 삭제는 없고 active=false로 내린다.")
    @ApiErrorCodes(ErrorCode.ADMIN_REQUIRED, ErrorCode.VALIDATION_FAILED, ErrorCode.CURATION_TAG_NOT_FOUND)
    @PatchMapping("/{curationTagId}")
    fun updateTag(
        @UserId userId: Long,
        @PathVariable curationTagId: String,
        @RequestBody request: UpdateCurationTagRequest,
    ): CurationTagAdminResponse =
        toResponse(
            updateCurationTagUseCase.update(
                curationTagId,
                CurationTagUpdateCommand(
                    label = request.label,
                    displayOrder = request.displayOrder,
                    active = request.active,
                ),
            ),
        )

    @Operation(summary = "칩의 매장 목록", description = "pin_order 순. 전체 교체를 보내기 전에 현재 상태를 알아야 한다.")
    @ApiErrorCodes(ErrorCode.ADMIN_REQUIRED, ErrorCode.CURATION_TAG_NOT_FOUND)
    @GetMapping("/{curationTagId}/places")
    fun listTagPlaces(
        @UserId userId: Long,
        @PathVariable curationTagId: String,
    ): ItemsResponse<CurationTagPlaceResponse> =
        ItemsResponse(getCurationTagPlacesUseCase.get(curationTagId).map(::toResponse))

    @Operation(
        summary = "칩의 매장 목록 (전체 교체)",
        description = "보낸 placeIds가 이 칩의 최종 집합이고 그 순서가 pin_order다. 빈 배열이면 칩을 비운다. 멱등이라 Idempotency-Key가 필요 없다.",
    )
    @ApiErrorCodes(
        ErrorCode.ADMIN_REQUIRED,
        ErrorCode.VALIDATION_FAILED,
        ErrorCode.CURATION_TAG_NOT_FOUND,
        ErrorCode.PLACE_NOT_FOUND,
    )
    @PutMapping("/{curationTagId}/places")
    fun replaceTagPlaces(
        @UserId userId: Long,
        @PathVariable curationTagId: String,
        @RequestBody request: ReplaceCurationTagPlacesRequest,
    ): ItemsResponse<CurationTagPlaceResponse> {
        val placeIds = request.placeIds.map(PublicIds::parsePlaceId)
        return ItemsResponse(replaceCurationTagPlacesUseCase.replace(curationTagId, placeIds).map(::toResponse))
    }

    private fun toResponse(view: CurationTagAdminView) =
        CurationTagAdminResponse(
            curationTagId = view.curationTagId,
            label = view.label,
            displayOrder = view.displayOrder,
            active = view.active,
            placeCount = view.placeCount,
        )

    private fun toResponse(view: CurationTagPlaceView) =
        CurationTagPlaceResponse(
            placeId = PublicIds.place(view.placeId),
            placeName = view.placeName,
            roadAddress = view.roadAddress,
            pinOrder = view.pinOrder,
        )

    @Schema(description = "큐레이션 칩 (운영)")
    data class CurationTagAdminResponse(
        @field:Schema(example = "curation_euljiro_yajang")
        val curationTagId: String,
        @field:Schema(example = "을지로 야장")
        val label: String,
        val displayOrder: Int,
        @field:Schema(description = "false면 공개 목록·검색에서 빠진다")
        val active: Boolean,
        @field:Schema(description = "지정된 매장 수. 0이면 칩을 눌러도 결과가 비어 있다")
        val placeCount: Int,
    )

    @Schema(description = "칩에 지정된 매장")
    data class CurationTagPlaceResponse(
        @field:Schema(example = "place_1")
        val placeId: String,
        val placeName: String,
        val roadAddress: String,
        val pinOrder: Int,
    )

    data class CreateCurationTagRequest(
        @field:Schema(description = "'curation_' + 영소문자·숫자·밑줄 1~20자", example = "curation_mapo_pork")
        val curationTagId: String,
        @field:Schema(description = "화면 노출 문구, 1~30자", example = "마포 돼지고기")
        val label: String,
        @field:Schema(description = "화면 칩 배열 순서, 0~999", example = "5")
        val displayOrder: Int,
    )

    @Schema(description = "보낸 필드만 바뀐다 — 전부 null이면 400")
    data class UpdateCurationTagRequest(
        val label: String? = null,
        val displayOrder: Int? = null,
        val active: Boolean? = null,
    )

    data class ReplaceCurationTagPlacesRequest(
        @field:Schema(description = "최종 집합. 순서가 pin_order가 된다. 최대 100개, 중복 불가", example = "[\"place_1\", \"place_2\"]")
        val placeIds: List<String>,
    )
}
