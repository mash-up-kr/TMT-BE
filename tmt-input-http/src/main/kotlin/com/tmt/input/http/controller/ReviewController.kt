package com.tmt.input.http.controller

import com.tmt.application.port.input.DeleteReviewUseCase
import com.tmt.application.port.input.GetReviewDetailUseCase
import com.tmt.common.exception.ErrorCode
import com.tmt.input.http.auth.UserId
import com.tmt.input.http.config.ApiErrorCodes
import com.tmt.input.http.controller.dto.response.Author
import com.tmt.input.http.controller.dto.response.PublicIds
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * 공개 리뷰 상세·삭제 실구현 (TMT-226) — mock을 대체한다. 응답 형태는 mock과 같고
 * reviewId 표기만 실구현의 `rv_` 접두를 따른다 (TMT-228 결정).
 */
@Tag(name = "리뷰 상세", description = "명세 v2 — I §6-3·§6-4")
@RestController
@RequestMapping("/v1/reviews/{reviewId}")
class ReviewController(
    private val getReviewDetailUseCase: GetReviewDetailUseCase,
    private val deleteReviewUseCase: DeleteReviewUseCase,
) {
    @Operation(
        summary = "공개 리뷰 상세",
        description = "완성된 리뷰만 대상이고 미완성 저장은 조회되지 않는다 (R8). 개인 리뷰 열람에는 게이트가 없다 (G2).",
    )
    @ApiErrorCodes(ErrorCode.REVIEW_NOT_FOUND)
    @GetMapping
    fun reviewDetail(
        @UserId userId: Long?,
        @PathVariable reviewId: String,
    ): ReviewDetailResponse {
        val detail = getReviewDetailUseCase.get(userId, PublicIds.parseReviewId(reviewId))
        return ReviewDetailResponse(
            reviewId = PublicIds.review(detail.reviewId),
            author =
                Author(
                    userId = PublicIds.user(detail.author.userId),
                    nickname = detail.author.nickname,
                    profileImageUrl = detail.author.profileImageUrl,
                ),
            place =
                ReviewDetailResponse.PlaceSummary(
                    placeId = PublicIds.place(detail.place.placeId),
                    name = detail.place.name,
                    roadAddress = detail.place.roadAddress,
                    categoryName = detail.place.categoryName,
                ),
            photos =
                detail.photos.map {
                    ReviewDetailResponse.Photo(
                        photoId = PublicIds.savePhoto(it.photoId),
                        url = it.url,
                        order = it.order,
                    )
                },
            tags = detail.tags.map { ReviewDetailResponse.Tag(it.tagId, it.label) },
            rating = detail.rating,
            content = detail.content,
            aiSummary = detail.aiSummary?.let { ReviewDetailResponse.AiSummary(it.pros, it.cons) },
            isMine = detail.isMine,
            createdAt = detail.createdAt.toString(),
        )
    }

    @Operation(
        summary = "리뷰 삭제",
        description = "사진까지 완전 삭제되고 저장으로 되돌아가지 않으며 티켓 1장을 회수한다 (R6·R7). 회수할 티켓이 없으면 409로 거부한다.",
    )
    @ApiErrorCodes(ErrorCode.REVIEW_NOT_FOUND, ErrorCode.REVIEW_DELETE_TICKET_REQUIRED)
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteReview(
        @UserId userId: Long,
        @PathVariable reviewId: String,
    ) {
        deleteReviewUseCase.delete(userId, PublicIds.parseReviewId(reviewId))
    }

    data class ReviewDetailResponse(
        val reviewId: String,
        val author: Author,
        val place: PlaceSummary,
        val photos: List<Photo>,
        val tags: List<Tag>,
        val rating: Int,
        val content: String,
        /** 생성 전·실패면 null (A2). */
        val aiSummary: AiSummary?,
        /** 비로그인이면 false. */
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
