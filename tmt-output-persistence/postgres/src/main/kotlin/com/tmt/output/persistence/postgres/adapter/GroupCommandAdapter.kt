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

        regionTagRepository.deleteAllByGroupId(groupId)
        regionTagRepository.saveAll(regionTagIds.map { GroupRegionTagEntity(GroupRegionTagId(groupId, it)) })
    }

    private fun <T> duplicateNameToError(block: () -> T): T =
        try {
            block()
        } catch (e: DataIntegrityViolationException) {
            throw TmtException(ErrorCode.GROUP_NAME_DUPLICATED)
        }
}
