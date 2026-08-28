package com.tmt.input.http.controller.dto.response

import com.tmt.application.port.input.ReviewCardView

/** ReviewCardView(읽기 모델) → 응답 DTO. 근처 피드·가게 상세·홈 실구현이 같이 쓴다. */
fun ReviewCardView.toResponse(): ReviewCardResponse =
    ReviewCardResponse(
        reviewId = PublicIds.review(reviewId),
        author =
            Author(
                userId = PublicIds.user(authorId),
                nickname = authorNickname,
                profileImageUrl = authorProfileImageUrl,
            ),
        rating = rating,
        distanceMeters = distanceMeters,
        photos = photos.map { ReviewCardResponse.Photo(PublicIds.savePhoto(it.photoId), it.url, it.order) },
        aiSummary = aiSummary?.let { ReviewCardResponse.AiSummary(it.pros, it.cons) },
        content = content,
        contentLength = content.codePointCount(0, content.length),
        tags = tags.map { ReviewCardResponse.Tag(it.tagId, it.label) },
        place =
            ReviewCardResponse.PlaceRegionSummary(
                placeId = PublicIds.place(placeId),
                name = placeName,
                regionName = placeRegionName,
                categoryName = placeCategoryName,
                isFavorite = placeFavorite,
            ),
        createdAt = createdAt.toString(),
    )
