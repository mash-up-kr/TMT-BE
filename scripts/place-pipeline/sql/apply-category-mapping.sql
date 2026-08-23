-- 카테고리 매핑 적용 (TMT-162) — 상권업종소분류명 43종 → 카테고리 14종 (E11 · D4)
--
-- 매핑표 정본: 장민서 작성, TMT-162 코멘트 (2026-08-23). 판단 근거 요약:
--   치킨→패스트푸드(버거와 동일 성격) · 피자→양식 · 족발/보쌈→한식(구이 아님 찜·수육)
--   복 요리→해산물 · 구내식당→한식 · 유흥 주점 2종→cat_bar(빼면 cat_bar가 0건, 문제 시 UPDATE로 재조정)
-- 매핑 실패는 NULL로 남긴다 — 임의로 기타에 몰지 않는다 (E11).
--
-- 전량 재계산이라 몇 번을 돌려도 같은 결과다. 매핑표를 고친 뒤 이 파일만 다시
-- 실행하면 재적재 없이 반영된다 (place_semas_category가 원본 소분류를 보존).

UPDATE place p
SET category_id = m.category_id
FROM place_semas_category r
LEFT JOIN (VALUES
    -- cat_korean 한식 (8)
    ('백반/한정식', 'cat_korean'), ('국/탕/찌개류', 'cat_korean'), ('국수/칼국수', 'cat_korean'),
    ('족발/보쌈', 'cat_korean'), ('냉면/밀면', 'cat_korean'), ('기타 한식 음식점', 'cat_korean'),
    ('전/부침개', 'cat_korean'), ('구내식당', 'cat_korean'),
    -- cat_cafe 카페·디저트 (4)
    ('카페', 'cat_cafe'), ('빵/도넛', 'cat_cafe'), ('떡/한과', 'cat_cafe'), ('아이스크림/빙수', 'cat_cafe'),
    -- cat_pub 주점 (2)
    ('요리 주점', 'cat_pub'), ('생맥주 전문', 'cat_pub'),
    -- cat_meat 고기·구이 (4)
    ('돼지고기 구이/찜', 'cat_meat'), ('소고기 구이/찜', 'cat_meat'),
    ('닭/오리고기 구이/찜', 'cat_meat'), ('곱창 전골/구이', 'cat_meat'),
    -- cat_western 양식 (5)
    ('경양식', 'cat_western'), ('피자', 'cat_western'), ('파스타/스테이크', 'cat_western'),
    ('기타 서양식 음식점', 'cat_western'), ('패밀리레스토랑', 'cat_western'),
    -- cat_bunsik 분식 (1)
    ('김밥/만두/분식', 'cat_bunsik'),
    -- cat_japanese 일식 (4)
    ('일식 회/초밥', 'cat_japanese'), ('일식 면 요리', 'cat_japanese'),
    ('일식 카레/돈가스/덮밥', 'cat_japanese'), ('기타 일식 음식점', 'cat_japanese'),
    -- cat_fastfood 패스트푸드 (2)
    ('치킨', 'cat_fastfood'), ('버거', 'cat_fastfood'),
    -- cat_chinese 중식 (2)
    ('중국집', 'cat_chinese'), ('마라탕/훠궈', 'cat_chinese'),
    -- cat_seafood 해산물 (3)
    ('횟집', 'cat_seafood'), ('해산물 구이/찜', 'cat_seafood'), ('복 요리 전문', 'cat_seafood'),
    -- cat_bar 바 (2)
    ('일반 유흥 주점', 'cat_bar'), ('무도 유흥 주점', 'cat_bar'),
    -- cat_asian 아시안 (2)
    ('베트남식 전문', 'cat_asian'), ('기타 동남아식 전문', 'cat_asian'),
    -- cat_brunch 브런치 (1)
    ('토스트/샌드위치/샐러드', 'cat_brunch'),
    -- cat_buffet 뷔페 (1)
    ('뷔페', 'cat_buffet')
    -- 의도적 NULL: '그 외 기타 간이 음식점' · '분류 안된 외국식 음식점'
    -- 주의: 소분류명은 실데이터 값 기준이다 — 실측 문서 §5-1 표기는 접미사(음식점/전문)를
    -- 생략하고 있어 그대로 옮기면 966건이 미매칭된다 (2026-08-23 로컬 전수 대조로 발견)
) AS m(category_source, category_id) ON m.category_source = r.category_source
WHERE p.external_source = 'SEMAS'
  AND p.external_id = r.external_id
  AND p.category_id IS DISTINCT FROM m.category_id;

-- 리포트 1 — 매핑률 (승인 기준: 측정 필수)
SELECT
    count(*)                                                        AS total,
    count(category_id)                                              AS mapped,
    round(count(category_id) * 100.0 / count(*), 1)                 AS mapped_pct
FROM place
WHERE external_source = 'SEMAS';

-- 리포트 2 — 카테고리별 분포
SELECT coalesce(category_id, '(NULL)') AS category, count(*) AS cnt
FROM place
WHERE external_source = 'SEMAS'
GROUP BY 1 ORDER BY 2 DESC;

-- 리포트 3 — 매핑표에 없는 소분류 (민서 회신용. 의도적 NULL 2종 외에 나오면 매핑표 보완 대상)
SELECT r.category_source, count(*) AS cnt
FROM place_semas_category r
JOIN place p ON p.external_source = 'SEMAS' AND p.external_id = r.external_id
WHERE p.category_id IS NULL
GROUP BY 1 ORDER BY 2 DESC;
