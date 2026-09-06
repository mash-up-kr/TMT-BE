package com.tmt.output.persistence.postgres.repository

/**
 * 그룹 커버 이미지의 정본 SQL (G16) — 커버는 **그 그룹에 공유된 리뷰의 사진**이고,
 * 리뷰 최신순, 한 리뷰 안에서는 `photo_order` 순이다.
 *
 * 같은 자구가 그룹 카드(1장)와 그룹 상세(N장) 두 곳에 필요한데, 규칙이 하나인데 SQL이 둘이면
 * 한쪽만 고쳐져 **같은 그룹의 커버가 화면마다 달라지는** 결함이 조용히 생긴다. 실제로 TMT-305 이전에는
 * 홈에도 같은 LATERAL이 복사돼 세 벌이었다.
 *
 * `s`(group_review_share)·`r`(review)·`sp`(save_photo)·`ma`(media_asset) 별칭을 쓰므로
 * 이 조각을 끼우는 쿼리는 같은 이름을 다른 뜻으로 쓰면 안 된다. 상관 조건(`WHERE s.group_id = ...`)과
 * 개수 제한은 쓰는 쪽이 붙인다 — 카드는 `LIMIT 1`, 상세는 `LIMIT :limit`이다.
 *
 * `const`라 `@Query` 애너테이션 안에서 문자열 템플릿으로 펼쳐진다.
 */
object GroupCoverSql {
    /** 공유 리뷰 → 사진 → 미디어. 삭제된 리뷰는 커버가 되지 않는다 (R6). */
    const val FROM_JOINS = """
            FROM group_review_share s
            JOIN review r       ON r.id = s.review_id AND r.deleted_at IS NULL
            JOIN save_photo sp  ON sp.save_id = r.save_id
            JOIN media_asset ma ON ma.id = sp.media_asset_id
    """

    /** 리뷰 최신순 → 리뷰 안에서는 사진 순서. `r.id`는 같은 시각의 tie-breaker다. */
    const val ORDER_BY = "ORDER BY r.created_at DESC, r.id DESC, sp.photo_order"
}
