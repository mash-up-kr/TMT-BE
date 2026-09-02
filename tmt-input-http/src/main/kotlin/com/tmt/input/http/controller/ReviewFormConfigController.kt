package com.tmt.input.http.controller

import com.tmt.application.port.input.GetReviewFormConfigUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 리뷰 폼 설정 실구현 (TMT-225). 응답 형태는 mock과 같다. */
@Tag(name = "리뷰 작성", description = "명세 v2 — F. 리뷰 작성")
@RestController
@RequestMapping("/v1/review-form-config")
class ReviewFormConfigController(
    private val getReviewFormConfigUseCase: GetReviewFormConfigUseCase,
) {
    @Operation(summary = "리뷰 폼 제약·태그 목록", description = "폼 제약과 태그 목록을 한 번에 내린다. 화면의 태그 칩은 이 응답으로 그린다.")
    @GetMapping
    fun reviewFormConfig(): ReviewFormConfigResponse {
        val config = getReviewFormConfigUseCase.get()
        return ReviewFormConfigResponse(
            photo =
                PhotoConstraint(
                    maxCount = config.photoMaxCount,
                    maxBytes = config.photoMaxBytes,
                    allowedContentTypes = config.allowedContentTypes,
                ),
            rating = RatingConstraint(min = config.ratingMin, max = config.ratingMax, step = config.ratingStep),
            content = ContentConstraint(maxLength = config.contentMaxLength),
            companionTags = config.companionTags.map { TagDefinition(it.tagId, it.label) },
            positivePointTags = config.positivePointTags.map { TagDefinition(it.tagId, it.label) },
        )
    }

    data class ReviewFormConfigResponse(
        val photo: PhotoConstraint,
        val rating: RatingConstraint,
        val content: ContentConstraint,
        val companionTags: List<TagDefinition>,
        val positivePointTags: List<TagDefinition>,
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

    data class TagDefinition(
        val tagId: String,
        val label: String,
    )
}
