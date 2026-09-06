package com.tmt.application.domain.group

import com.tmt.application.domain.media.MediaUrlResolver
import com.tmt.application.port.input.AttachMediaUseCase
import com.tmt.application.port.input.GroupCommand
import com.tmt.application.port.output.persistence.GroupCommandPort
import com.tmt.application.port.output.persistence.GroupCoverImageRow
import com.tmt.application.port.output.persistence.GroupDetailPort
import com.tmt.application.port.output.persistence.GroupDetailRow
import com.tmt.application.port.output.persistence.GroupEditTarget
import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class GroupCommandServiceTest {
    private var created: List<Any?>? = null
    private var updated: List<Any?>? = null
    private var editTarget: GroupEditTarget? = GroupEditTarget(ownerId = 1L, imageAssetId = null)
    private var detailRow: GroupDetailRow = row()

    private val commandPort =
        object : GroupCommandPort {
            override fun create(
                ownerId: Long,
                name: String,
                oneLineDescription: String,
                description: String?,
                foodCategoryId: String,
                regionTagIds: List<String>,
                imageAssetId: Long?,
            ): Long {
                created = listOf(ownerId, name, regionTagIds, imageAssetId)
                return 10L
            }

            override fun findEditTarget(groupId: Long) = editTarget

            override fun update(
                groupId: Long,
                name: String,
                oneLineDescription: String,
                description: String?,
                foodCategoryId: String,
                regionTagIds: List<String>,
                imageAssetId: Long?,
            ) {
                updated = listOf(groupId, name, regionTagIds, imageAssetId)
            }
        }

    private val detailPort =
        object : GroupDetailPort {
            override fun findDetail(
                groupId: Long,
                viewerId: Long?,
            ) = detailRow

            override fun findRegionTagIds(groupId: Long) = listOf("region_guro")

            override fun findCoverImages(
                groupId: Long,
                limit: Int,
            ) = listOf(GroupCoverImageRow("review/c.jpg", 3L))
        }

    private val verifyCalls = mutableListOf<Triple<Long, List<Long>, Set<Long>>>()
    private val attachedIds = mutableListOf<Long>()
    private val detachedIds = mutableListOf<Long>()

    private val attachMedia =
        object : AttachMediaUseCase {
            override fun verifyAttachable(
                ownerId: Long,
                assetIds: List<Long>,
                reattachableIds: Set<Long>,
            ) {
                verifyCalls += Triple(ownerId, assetIds, reattachableIds)
            }

            override fun attach(
                assetIds: List<Long>,
                reattachableIds: Set<Long>,
            ) {
                attachedIds += assetIds
            }

            override fun detach(assetIds: List<Long>) {
                detachedIds += assetIds
            }
        }

    private val service =
        GroupCommandService(
            commandPort,
            attachMedia,
            GroupDetailComposer(detailPort, MediaUrlResolver("https://media.example.com")),
        )

    @Test
    fun `생성하면 요청자가 소유자이고 이미지는 ATTACHED로 전이된다 (G13·M7)`() {
        val view = service.create(command(imageAssetId = 42L))

        assertEquals(listOf<Any?>(1L, "새 그룹", listOf("region_guro"), 42L), created)
        assertEquals(listOf(42L), attachedIds)
        assertEquals(10L, view.groupId)
        assertEquals(true, view.isOwner)
        assertEquals("https://media.example.com/review/c.jpg", view.coverImages[0].url)
    }

    @Test
    fun `지역 태그가 비어 있으면 VALIDATION_FAILED다 (G7)`() {
        val e = assertThrows<TmtException> { service.create(command(regionTagIds = emptyList())) }
        assertEquals(ErrorCode.VALIDATION_FAILED, e.errorCode)
    }

    @Test
    fun `정의에 없는 태그는 GROUP_TAG_NOT_FOUND다`() {
        val e = assertThrows<TmtException> { service.create(command(foodCategoryId = "cat_nope")) }
        assertEquals(ErrorCode.GROUP_TAG_NOT_FOUND, e.errorCode)
    }

    @Test
    fun `편집은 생성자만 할 수 있다 (G13)`() {
        editTarget = GroupEditTarget(ownerId = 99L, imageAssetId = null)
        val e = assertThrows<TmtException> { service.update(10L, command()) }
        assertEquals(ErrorCode.GROUP_OWNER_REQUIRED, e.errorCode)
    }

    @Test
    fun `없는 그룹 편집은 GROUP_NOT_FOUND다`() {
        editTarget = null
        val e = assertThrows<TmtException> { service.update(10L, command()) }
        assertEquals(ErrorCode.GROUP_NOT_FOUND, e.errorCode)
    }

    @Test
    fun `이미지를 교체하면 이전 asset은 STAGED로 돌아간다 (M4)`() {
        editTarget = GroupEditTarget(ownerId = 1L, imageAssetId = 41L)

        service.update(10L, command(imageAssetId = 42L))

        assertEquals(listOf(41L), detachedIds)
        assertEquals(listOf(42L), attachedIds)
    }

    @Test
    fun `이미지를 그대로 둔 편집은 재부착으로 막히지 않는다`() {
        editTarget = GroupEditTarget(ownerId = 1L, imageAssetId = 42L)

        service.update(10L, command(imageAssetId = 42L))

        // 검증에는 재부착 허용 집합으로 전달되고, 전이는 일어나지 않는다
        assertEquals(setOf(42L), verifyCalls.single().third)
        assertTrue(attachedIds.isEmpty())
        assertTrue(detachedIds.isEmpty())
    }

    @Test
    fun `이미지를 지우면 이전 asset이 STAGED로 돌아간다 (M4)`() {
        editTarget = GroupEditTarget(ownerId = 1L, imageAssetId = 41L)

        service.update(10L, command(imageAssetId = null))

        assertEquals(listOf(41L), detachedIds)
        assertTrue(attachedIds.isEmpty())
    }

    private fun command(
        foodCategoryId: String = "cat_korean",
        regionTagIds: List<String> = listOf("region_guro"),
        imageAssetId: Long? = null,
    ) = GroupCommand(
        requesterId = 1L,
        name = "새 그룹",
        oneLineDescription = "한줄",
        description = null,
        foodCategoryId = foodCategoryId,
        regionTagIds = regionTagIds,
        imageAssetId = imageAssetId,
    )

    private fun row() =
        GroupDetailRow(
            groupId = 10L,
            name = "새 그룹",
            oneLineDescription = "한줄",
            description = null,
            imageS3Key = null,
            ownerId = 1L,
            memberCount = 1,
            reviewCount = 0,
            placeCount = 0,
            foodCategoryId = "cat_korean",
            matchedSavedPlaceCount = 0,
            isMember = true,
        )
}
