package com.tmt.application.domain.save

import com.tmt.application.domain.media.MediaRules
import com.tmt.application.port.input.GetReviewFormConfigUseCase
import com.tmt.application.port.input.ReviewFormConfigView
import com.tmt.application.port.input.TagDefinitionView
import com.tmt.application.port.output.persistence.ReviewTagPort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 리뷰 폼 제약·태그 목록 (F §3-1). 제약은 SaveRules·MediaRules, 태그는 `review_tag_definition`이 정본이다. */
@Service
@Transactional(readOnly = true)
class ReviewFormConfigService(
    private val reviewTagPort: ReviewTagPort,
) : GetReviewFormConfigUseCase {
    override fun get(): ReviewFormConfigView {
        val definitions = reviewTagPort.findAllActiveDefinitions()
        return ReviewFormConfigView(
            photoMaxCount = SaveRules.PHOTO_MAX_COUNT,
            photoMaxBytes = MediaRules.MAX_CONTENT_LENGTH,
            allowedContentTypes = MediaRules.ALLOWED_CONTENT_TYPES.toList(),
            ratingMin = SaveRules.RATING_MIN,
            ratingMax = SaveRules.RATING_MAX,
            ratingStep = SaveRules.RATING_STEP,
            contentMaxLength = SaveRules.CONTENT_MAX_LENGTH,
            companionTags = definitions.filter { it.companion }.map { TagDefinitionView(it.tagId, it.label) },
            positivePointTags = definitions.filterNot { it.companion }.map { TagDefinitionView(it.tagId, it.label) },
        )
    }
}
