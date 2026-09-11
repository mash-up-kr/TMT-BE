-- 큐레이션 칩 4종과 각 칩의 초기 매장 목록 (E12)
--
-- 칩 id·문구·순서는 종전 서버 상수(CurationPresets)와 같은 값이다 — 여기서 바꾸면
-- FE가 받는 목록이 함께 바뀌므로 값 변경은 계약 변경 이력을 거친다.

INSERT INTO curation_tag (id, label, display_order) VALUES
    ('curation_euljiro_yajang', '을지로 야장',  1),
    ('curation_ganmaek',        '간맥집',      2),
    ('curation_butteotteok',    '버터떡 카페',  3),
    ('curation_lamb',           '양갈비',      4);

-- 초기 목록은 **종전 프리셋 조건을 한 번 실행한 결과**다. 상수 시절의 동작을 그대로
-- 재현해 두고, 이후 운영이 손으로 고친다 (질문 36 "운영이 매장을 수동 매핑").
--
-- 칩당 30개로 자르고 리뷰 많은 순으로 고른다 — 조건만으로는 "을지로 야장"이 중구 전체
-- (수천 건)와 같아서, 그대로 넣으면 운영이 손댈 수 없는 크기가 된다. 리뷰가 있는 매장을
-- 앞세우는 건 칩이 서는 자리(근처 탐색)와 같은 기준이다 (E6).
--
-- place가 아직 비어 있는 환경(파이프라인 반입 전)에서는 목록이 비고, 칩은 빈 결과를
-- 돌려준다. 그때는 이 파일을 고치지 말고 반입 후 운영 경로로 채운다.
--
-- **실측(2026-09-11, V1~V10 빈 볼륨 기동)**: V5 시드의 매장 24건에서 을지로 야장 1 · 간맥집 3 ·
-- 양갈비 7 · 버터떡 카페 **0**이 들어간다. cat_cafe 매장이 시드에 없어서다 — 로컬·CI에서 그 칩만
-- 빈 결과인 것은 정상이고, 실데이터(서울 전체) 반입 환경에서는 30개까지 찬다.
INSERT INTO curation_tag_place (curation_tag_id, place_id, pin_order)
SELECT c.curation_tag_id, c.place_id, CAST(c.rn AS smallint)
FROM (
    SELECT t.curation_tag_id,
           p.id AS place_id,
           row_number() OVER (PARTITION BY t.curation_tag_id ORDER BY p.review_count DESC, p.id) AS rn
    FROM place p
    JOIN (
        -- 종전 CurationPresets의 (categoryId, regionPrefix) 그대로
        -- NULL에 명시적 캐스트를 붙인다 — VALUES의 컬럼 타입이 unknown으로 남으면
        -- 비교 연산자 해석이 환경에 맡겨진다
        VALUES ('curation_euljiro_yajang', CAST(NULL AS text), CAST('중구' AS text)),
               ('curation_ganmaek',        'cat_pub',          CAST(NULL AS text)),
               ('curation_butteotteok',    'cat_cafe',         CAST(NULL AS text)),
               ('curation_lamb',           'cat_meat',         CAST(NULL AS text))
    ) AS t(curation_tag_id, category_id, region_prefix)
      ON (t.category_id IS NULL OR p.category_id = t.category_id)
     AND (t.region_prefix IS NULL OR p.region_name LIKE t.region_prefix || '%')
) c
WHERE c.rn <= 30;
