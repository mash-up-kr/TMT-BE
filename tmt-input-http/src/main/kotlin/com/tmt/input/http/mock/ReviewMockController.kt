package com.tmt.input.http.mock

import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException
import com.tmt.input.http.auth.UserId
import com.tmt.input.http.controller.dto.response.Author
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@Tag(name = "리뷰 상세 (mock)", description = "명세 v2 — I §6-3·§6-4")
@RestController
@RequestMapping("/v1/reviews/{reviewId}")
class ReviewMockController(
    private val mockSaveStore: InMemoryStore<MockSave>,
    private val mockPlaceStore: InMemoryStore<MockPlace>,
    private val mockAssetStore: InMemoryStore<MockAsset>,
    private val mockTicketLedger: MockTicketLedger,
    private val mockAiSummaryStore: MockAiSummaryStore,
) {
    @Operation(summary = "공개 리뷰 상세", description = "완성된 리뷰만 대상이고 미완성 저장은 조회되지 않는다 (R8). 개인 리뷰 열람에는 게이트가 없다 (G2).")
    @GetMapping
    fun reviewDetail(
        @UserId userId: Long?,
        @PathVariable reviewId: String,
    ): ReviewDetailResponse {
        val save = findReview(reviewId) ?: throw TmtException(ErrorCode.REVIEW_NOT_FOUND)
        val place = mockPlaceStore.findById(save.placeId)
        return ReviewDetailResponse(
            reviewId = reviewId,
            author = MockUsers.authorOf(save.ownerId),
            place =
                ReviewDetailResponse.PlaceSummary(
                    placeId = save.placeId,
                    name = place?.name ?: "(삭제된 매장)",
                    roadAddress = place?.roadAddress ?: "",
                    categoryName = place?.categoryName,
                ),
            photos =
                save.photoAssetIds.mapIndexed { index, assetId ->
                    ReviewDetailResponse.Photo(
                        photoId = "sp_${assetId.removePrefix("asset_")}",
                        url = mockMediaUrl(assetId),
                        order = index,
                    )
                },
            tags =
                (save.companionTagIds + save.positivePointTagIds).map {
                    ReviewDetailResponse.Tag(it, ReviewFormRules.labelOf(it))
                },
            rating = requireNotNull(save.rating) { "리뷰는 별점이 필수라 null이 아니다 (C4)" },
            content = requireNotNull(save.content),
            aiSummary = mockAiSummaryStore.find(reviewId)?.let { ReviewDetailResponse.AiSummary(it.pros, it.cons) },
            isMine = userId != null && save.ownerId == userId,
            createdAt = save.createdAt.toString(),
        )
    }

    @Operation(
        summary = "리뷰 삭제",
        description = "사진까지 완전 삭제되고 저장으로 되돌아가지 않으며 티켓 1장을 회수한다 (R6·R7). 회수할 티켓이 없으면 409로 거부한다.",
    )
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteReview(
        @UserId userId: Long,
        @PathVariable reviewId: String,
    ) {
        val save =
            findReview(reviewId)?.takeIf { it.ownerId == userId }
                ?: throw TmtException(ErrorCode.REVIEW_NOT_FOUND)

        if (!mockTicketLedger.tryConsume(userId)) {
            throw ReviewDeleteTicketRequiredException(availableCount = mockTicketLedger.availableCount(userId))
        }
        save.photoAssetIds.forEach { mockAssetStore.delete(it) }
        mockSaveStore.delete(save.saveId)
    }

    private fun findReview(reviewId: String): MockSave? = mockSaveStore.findAll().find { it.reviewId == reviewId }

    data class ReviewDetailResponse(
        val reviewId: String,
        val author: Author,
        val place: PlaceSummary,
        val photos: List<Photo>,
        val tags: List<Tag>,
        val rating: Int,
        val content: String,
        val aiSummary: AiSummary?,
        val isMine: Boolean,
        val createdAt: String,
    ) {
        data class PlaceSummary(
            val placeId: String,
            val name: String,
            val roadAddress: String,
            val categoryName: String?,
        )

        data class Photo(
            val photoId: String,
            val url: String,
            val order: Int,
        )

        data class Tag(
            val tagId: String,
            val label: String,
        )

        data class AiSummary(
            val pros: String?,
            val cons: String?,
        )
    }
}
