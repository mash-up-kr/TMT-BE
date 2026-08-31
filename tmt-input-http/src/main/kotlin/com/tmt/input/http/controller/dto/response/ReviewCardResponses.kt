package com.tmt.input.http.controller.dto.response

import com.tmt.application.port.input.ReviewCardView

/**
 * ReviewCardView(읽기 모델) → 응답 DTO. 근처 피드·가게 상세·그룹 상세·홈 실구현이 같이 쓴다.
 * [masked]는 미가입 그룹 리뷰 목록 — 본문·단점 요약을 서버가 지운다 (G1, TMT-216).
 * contentLength는 마스킹 여부와 무관하게 원본 기준이다 — 화면이 그 길이만큼 블러 자리를 잡는다.
 */
fun ReviewCardView.toResponse(masked: Boolean = false): ReviewCardResponse =
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
        aiSummary = aiSummary?.let { ReviewCardResponse.AiSummary(it.pros, if (masked) null else it.cons) },
        content = if (masked) null else content,
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
