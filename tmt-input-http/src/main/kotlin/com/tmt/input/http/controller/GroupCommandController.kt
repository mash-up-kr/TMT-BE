package com.tmt.input.http.controller

import com.tmt.application.port.input.CreateGroupUseCase
import com.tmt.application.port.input.GroupCommand
import com.tmt.application.port.input.UpdateGroupUseCase
import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException
import com.tmt.input.http.auth.UserId
import com.tmt.input.http.config.ApiErrorCodes
import com.tmt.input.http.controller.dto.response.GroupDetailResponse
import com.tmt.input.http.controller.dto.response.PublicIds
import com.tmt.input.http.controller.dto.response.toResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.net.URI

/**
 * 그룹 생성·편집 실구현 (TMT-221). 응답 형태·ID 표기(`group_`)는 mock과 같다.
 */
@Tag(name = "그룹", description = "명세 v2 — D_01. 그룹 탐색 · D_02. 그룹 생성·상세·편집")
@RestController
@RequestMapping("/v1/groups")
class GroupCommandController(
    private val createGroupUseCase: CreateGroupUseCase,
    private val updateGroupUseCase: UpdateGroupUseCase,
) {
    @Operation(summary = "그룹 만들기", description = "4단계 입력을 한 번에 보낸다. 요청자가 자동으로 그룹장이 되고 소유권은 이전되지 않는다 (G13).")
    @ApiErrorCodes(
        ErrorCode.GROUP_TAG_NOT_FOUND,
        ErrorCode.MEDIA_NOT_OWNED,
        ErrorCode.GROUP_NAME_DUPLICATED,
        ErrorCode.MEDIA_ALREADY_ATTACHED,
    )
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createGroup(
        @UserId userId: Long,
        @RequestBody request: GroupRequest,
    ): ResponseEntity<GroupDetailResponse> {
        val detail = createGroupUseCase.create(request.toCommand(userId))
        return ResponseEntity
            .created(URI.create("/v1/groups/group_${detail.groupId}"))
            .body(detail.toResponse())
    }

    @Operation(summary = "그룹 편집", description = "생성자만 호출할 수 있다 (G13). 전체 교체라 바꾸지 않는 필드도 현재 값을 실어 보내야 한다.")
    @ApiErrorCodes(
        ErrorCode.GROUP_TAG_NOT_FOUND,
        ErrorCode.MEDIA_NOT_OWNED,
        ErrorCode.GROUP_OWNER_REQUIRED,
        ErrorCode.GROUP_NOT_FOUND,
        ErrorCode.GROUP_NAME_DUPLICATED,
        ErrorCode.MEDIA_ALREADY_ATTACHED,
    )
    @PutMapping("/{groupId}")
    fun updateGroup(
        @UserId userId: Long,
        @PathVariable groupId: String,
        @RequestBody request: GroupRequest,
    ): GroupDetailResponse =
        updateGroupUseCase.update(PublicIds.parseGroupId(groupId), request.toCommand(userId)).toResponse()

    private fun GroupRequest.toCommand(requesterId: Long) =
        GroupCommand(
            requesterId = requesterId,
            name = name,
            oneLineDescription = oneLineDescription,
            description = description,
            foodCategoryId = foodCategoryId,
            regionTagIds = regionTagIds,
            imageAssetId = imageAssetId?.let(::parseAssetId),
        )

    /** 실구현 발급 assetId는 접두 없는 숫자 문자열이다 (TMT-202). 형식이 다르면 없는 사진과 같게 취급한다 (M2). */
    private fun parseAssetId(assetId: String): Long =
        assetId.toLongOrNull() ?: throw TmtException(ErrorCode.MEDIA_NOT_OWNED)

    data class GroupRequest(
        val name: String,
        val oneLineDescription: String,
        val foodCategoryId: String,
        val regionTagIds: List<String>,
        val imageAssetId: String? = null,
        val description: String? = null,
    )
}
