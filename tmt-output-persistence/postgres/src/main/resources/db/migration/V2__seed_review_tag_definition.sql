-- 리뷰 태그 12종 (동행 5 · 좋은 점 7). display_order는 tag_type 안에서의 노출 순서다.
-- review-form-config 응답과 같은 값이어야 한다 — 한쪽만 고치면 화면의 칩과 저장 가능한 태그가 어긋난다.

INSERT INTO review_tag_definition (id, tag_type, label, display_order) VALUES
    ('tag_alone',     'COMPANION',      '혼자',             1),
    ('tag_couple',    'COMPANION',      '연인',             2),
    ('tag_friend',    'COMPANION',      '친구',             3),
    ('tag_colleague', 'COMPANION',      '동료·지인',        4),
    ('tag_family',    'COMPANION',      '가족',             5),
    ('tag_tasty',     'POSITIVE_POINT', '음식이 맛있어요',   1),
    ('tag_kind',      'POSITIVE_POINT', '응대가 친절해요',   2),
    ('tag_mood',      'POSITIVE_POINT', '분위기가 좋아요',   3),
    ('tag_value',     'POSITIVE_POINT', '가성비가 좋아요',   4),
    ('tag_clean',     'POSITIVE_POINT', '청결하고 깔끔해요', 5),
    ('tag_transit',   'POSITIVE_POINT', '교통이 편리해요',   6),
    ('tag_spacious',  'POSITIVE_POINT', '자리가 넓고 편해요', 7);
