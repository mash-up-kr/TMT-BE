package com.tmt.application.domain.group

import com.tmt.application.port.input.GroupDetailView
import com.tmt.application.port.output.persistence.GroupDetailPort
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/** 그룹 상세 조립 (D_02 §3-1) — 생성·편집 응답(TMT-221)과 상세 조회(TMT-222)가 같이 쓴다. */
@Component
class GroupDetailComposer(
    private val groupDetailPort: GroupDetailPort,
    @param:Value("\${tmt.media.base-url:}") private val mediaBaseUrl: String,
) {
    fun compose(
        groupId: Long,
        viewerId: Long?,
    ): GroupDetailView? {
        val row = groupDetailPort.findDetail(groupId, viewerId) ?: return null
        return GroupDetailView(
            groupId = row.groupId,
            name = row.name,
            oneLineDescription = row.oneLineDescription,
            description = row.description,
            imageUrl = row.imageS3Key?.let(::mediaUrl),
            coverImages =
                groupDetailPort.findCoverImages(groupId, MAX_COVER_IMAGES).map {
                    GroupDetailView.CoverImage(url = mediaUrl(it.s3Key), reviewId = it.reviewId)
                },
            memberCount = row.memberCount,
            reviewCount = row.reviewCount,
            placeCount = row.placeCount,
            foodCategoryId = row.foodCategoryId,
            regionTagIds = groupDetailPort.findRegionTagIds(groupId),
            matchedSavedPlaceCount = row.matchedSavedPlaceCount,
            isMember = row.isMember,
            isOwner = viewerId != null && row.ownerId == viewerId,
        )
    }

    private fun mediaUrl(s3Key: String): String = "${mediaBaseUrl.trimEnd('/')}/$s3Key"

    companion object {
        /** 상세 커버 캐러셀 상한 — 카드 커버(1장)와 달리 상세는 5장이다 (G16). */
        const val MAX_COVER_IMAGES = 5
    }
}
