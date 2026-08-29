-- mock 기간의 X-User-Id 스텁이 가리키는 사용자. MockStoreConfig.SEED_USERS와 같은 집합이어야 한다 —
-- 사용자는 인메모리 mock에만 있는데 media_asset은 실구현이라(TMT-202) owner_id가 users(id)를 참조한다.
-- 한쪽만 고치면 사진 업로드가 FK 위반으로 실패한다 (TMT-252 확인 중 발견).
--
-- id는 GENERATED ALWAYS라 명시 삽입에 OVERRIDING SYSTEM VALUE가 필요하고,
-- 그만큼 IDENTITY 시퀀스를 앞으로 밀어야 카카오 로그인 이후 가입자와 충돌하지 않는다 (U1).
-- kakao_id는 실제 카카오 계정이 아니라 자리값이다 — 로그인이 붙으면 이 행들은 걷어낸다.

INSERT INTO users (id, kakao_id, nickname) OVERRIDING SYSTEM VALUE VALUES
    -- UT 대상자 4명 — X-User-Id 1~4가 그대로 이 사람들이다
    (1, 900000001, '조용한 미식가'),
    (2, 900000002, '매콤한 하루'),
    (3, 900000003, '면요리 연구가'),
    (4, 900000004, '커피 마시는 곰'),
    -- UT2 콘텐츠(TMT-213)의 persona 작성자 — 그룹장·리뷰 작성자
    (901, 900000901, '회사원 미식러'),
    (902, 900000902, '잠실 토박이'),
    (903, 900000903, '골목 탐험가')
ON CONFLICT (id) DO NOTHING;

SELECT setval(pg_get_serial_sequence('users', 'id'), 1000, false);
