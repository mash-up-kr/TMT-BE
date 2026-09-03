package com.tmt.output.persistence.postgres.adapter

import com.tmt.application.port.output.persistence.GroupCommandPort
import com.tmt.application.port.output.persistence.GroupEditTarget
import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException
import com.tmt.output.persistence.postgres.entity.GroupEntity
import com.tmt.output.persistence.postgres.entity.GroupMembershipEntity
import com.tmt.output.persistence.postgres.entity.GroupRegionTagEntity
import com.tmt.output.persistence.postgres.entity.GroupRegionTagId
import com.tmt.output.persistence.postgres.repository.GroupCommandRepository
import com.tmt.output.persistence.postgres.repository.GroupMembershipRepository
import com.tmt.output.persistence.postgres.repository.GroupRegionTagRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component

@Component
class GroupCommandAdapter(
    private val groupRepository: GroupCommandRepository,
    private val regionTagRepository: GroupRegionTagRepository,
    private val membershipRepository: GroupMembershipRepository,
) : GroupCommandPort {
    override fun create(
        ownerId: Long,
        name: String,
        oneLineDescription: String,
        description: String?,
        foodCategoryId: String,
        regionTagIds: List<String>,
        imageAssetId: Long?,
    ): Long {
        val group =
            duplicateNameToError {
                // flush로 UNIQUE 위반을 여기서 터뜨린다 — 커밋까지 미루면 잡을 곳이 없다 (G6, 경합 포함)
                groupRepository.saveAndFlush(
                    GroupEntity(
                        name = name,
                        oneLineDescription = oneLineDescription,
                        description = description,
                        imageAssetId = imageAssetId,
                        foodCategoryId = foodCategoryId,
                        ownerId = ownerId,
                    ),
                )
            }
        // 위 saveAndFlush가 던지면 여기 못 온다. 여기 온 뒤 아래가 던져도 태그·멤버십은
        // GroupCommandService.create의 @Transactional 롤백으로 함께 되돌아간다 — 이 전제(전파 속성)를 바꾸면 깨진다
        regionTagRepository.saveAll(regionTagIds.map { GroupRegionTagEntity(GroupRegionTagId(group.id, it)) })
        // 그룹장은 탈퇴할 수 없다(G11) = 생성자는 멤버다. member_count 기본값 1이 생성자 몫이다
        membershipRepository.save(GroupMembershipEntity(groupId = group.id, userId = ownerId))
        return group.id
    }

    override fun findEditTarget(groupId: Long): GroupEditTarget? =
        groupRepository
            .findById(groupId)
            .map { GroupEditTarget(ownerId = it.ownerId, imageAssetId = it.imageAssetId) }
            .orElse(null)

    override fun update(
        groupId: Long,
        name: String,
        oneLineDescription: String,
        description: String?,
        foodCategoryId: String,
        regionTagIds: List<String>,
        imageAssetId: Long?,
    ) {
        val group = groupRepository.findById(groupId).orElseThrow { TmtException(ErrorCode.GROUP_NOT_FOUND) }
        group.name = name
        group.oneLineDescription = oneLineDescription
        group.description = description
        group.foodCategoryId = foodCategoryId
        group.imageAssetId = imageAssetId
        duplicateNameToError { groupRepository.saveAndFlush(group) }

        // 집합 교체라 0행이 정상이다 — 태그가 없던 그룹도 같은 경로를 탄다
        regionTagRepository.deleteAllByGroupId(groupId)
        regionTagRepository.saveAll(regionTagIds.map { GroupRegionTagEntity(GroupRegionTagId(groupId, it)) })
    }

    private fun <T> duplicateNameToError(block: () -> T): T =
        try {
            block()
        } catch (e: DataIntegrityViolationException) {
            // 이름 UNIQUE만 409로 바꾼다 — image_asset_id·owner_id FK 위반 등 다른 무결성 오류까지
            // "이미 있는 그룹명"으로 내리면 진짜 원인이 숨는다 (PR #80 리뷰)
            if (e.mostSpecificCause.message?.contains(NAME_UNIQUE_CONSTRAINT) == true) {
                throw TmtException(ErrorCode.GROUP_NAME_DUPLICATED)
            }
            throw e
        }

    companion object {
        /** V1의 `name VARCHAR(50) NOT NULL UNIQUE`가 받는 Postgres 기본 제약명 */
        private const val NAME_UNIQUE_CONSTRAINT = "groups_name_key"
    }
}
