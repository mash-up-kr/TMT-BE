package com.tmt.input.http.mock

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "리뷰 작성 (mock)", description = "명세 v2 — F. 리뷰 작성 · G. 이어쓰기 · I. 리뷰 상세")
@RestController
@RequestMapping("/v1/review-form-config")
class ReviewFormConfigMockController {
    @Operation(summary = "리뷰 폼 제약·태그 목록", description = "폼 제약과 태그 목록을 한 번에 내린다. 화면의 태그 칩은 이 응답으로 그린다.")
    @GetMapping
    fun reviewFormConfig(): ReviewFormConfigResponse = RESPONSE

    data class ReviewFormConfigResponse(
        val photo: PhotoConstraint,
        val rating: RatingConstraint,
        val content: ContentConstraint,
        val companionTags: List<ReviewFormRules.TagDefinition>,
        val positivePointTags: List<ReviewFormRules.TagDefinition>,
    )

    data class PhotoConstraint(
        val maxCount: Int,
        val maxBytes: Long,
        val allowedContentTypes: List<String>,
    )

    data class RatingConstraint(
        val min: Int,
        val max: Int,
        val step: Int,
    )

    data class ContentConstraint(
        val maxLength: Int,
    )

    companion object {
        private val RESPONSE =
            ReviewFormConfigResponse(
                photo =
                    PhotoConstraint(
                        maxCount = ReviewFormRules.PHOTO_MAX_COUNT,
                        maxBytes = ReviewFormRules.PHOTO_MAX_BYTES,
                        allowedContentTypes = ReviewFormRules.ALLOWED_CONTENT_TYPES,
                    ),
                rating =
                    RatingConstraint(
                        min = ReviewFormRules.RATING_MIN,
                        max = ReviewFormRules.RATING_MAX,
                        step = ReviewFormRules.RATING_STEP,
                    ),
                content = ContentConstraint(maxLength = ReviewFormRules.CONTENT_MAX_LENGTH),
                companionTags = ReviewFormRules.COMPANION_TAGS,
                positivePointTags = ReviewFormRules.POSITIVE_POINT_TAGS,
            )
    }
}
