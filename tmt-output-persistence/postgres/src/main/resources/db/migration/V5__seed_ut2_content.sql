-- UT2 콘텐츠 시드 (TMT-231) — 정본은 mock의 MockUt2Seeds.kt(TMT-213). mock이 지워질 때 이 시드가 남는다.
-- V4의 mock 사용자(1~4·901~903)를 전제한다. 사진은 media 버킷 seed/ut2/*의 실제 객체다.
--
-- 매장은 이름 + 500m 근접으로 기존 행(공공데이터 적재분)에 먼저 매칭하고, 없으면
-- external_source='SEED_UT2'로 새로 넣는다 — 빈 로컬 볼륨과 141k가 적재된 운영 어느 쪽에서도 돈다.
-- 파생 집계(P9·D3)는 증분이 아니라 실제 행 수에서 재계산해 넣는다. 검증: scripts/seed/verify_ut2_seed.sql
--
-- 이 파일은 재실행 안전(sentinel + ON CONFLICT)하지만, Flyway 체크섬 때문에 적용 후 수정하지 않는다.
DO $seed$
DECLARE
  sp_row RECORD;
  v_place  BIGINT;
  v_save   BIGINT;
  v_review BIGINT;
  v_asset  BIGINT;
  v_grant  BIGINT;
  v_gid    BIGINT;
  v_group_created CONSTANT timestamptz := TIMESTAMPTZ '2026-08-18 03:00:00+00';
  v_reviewed      CONSTANT timestamptz := TIMESTAMPTZ '2026-08-19 10:00:00+00';
  v_at timestamptz;
BEGIN
  -- sentinel: 그룹 하나라도 있으면 이미 시드된 DB다
  IF EXISTS (SELECT 1 FROM groups WHERE name = '한국인인데 김치보다 회를 더 좋아하는 한국 사람 추천 숙성회 맛집') THEN
    RAISE NOTICE 'UT2 seed already present — skipping';
    RETURN;
  END IF;

  -- ── 매장: 이름+근접 매칭, 없으면 삽입 ──────────────────────────
  CREATE TEMP TABLE _sp (
    key text PRIMARY KEY, name text, road text, region text, cat text,
    lat float8, lon float8, place_id BIGINT
  ) ON COMMIT DROP;

  INSERT INTO _sp VALUES ('sasanoha', '사사노하', '서울 송파구 백제고분로42길 4-13', '송파구 송파동', 'cat_pub', 37.5056, 127.10897, NULL);
  INSERT INTO _sp VALUES ('dorimhang', '도림항 본점', '서울 관악구 조원로4길 8', '관악구 신림동', 'cat_seafood', 37.4829942, 126.9043818, NULL);
  INSERT INTO _sp VALUES ('mukeunji', '우리집 묵은지 생삼겹', '서울 구로구 구로동', '구로구 구로동', 'cat_meat', 37.4835, 126.8975, NULL);
  INSERT INTO _sp VALUES ('keunjip', '큰집', '서울 구로구 도림로10길 23', '구로구 구로동', 'cat_meat', 37.4857827, 126.8887635, NULL);
  INSERT INTO _sp VALUES ('malttuk', '말뚝곱창', '서울 구로구 시흥대로 571', '구로구 구로동', 'cat_meat', 37.4839742, 126.901814, NULL);
  INSERT INTO _sp VALUES ('niwasushi', '니와스시참치', '서울 구로구 시흥대로163길 33', '구로구 구로동', 'cat_japanese', 37.4819979, 126.8980968, NULL);
  INSERT INTO _sp VALUES ('ohansu', '오한수우육면가', '서울 구로구 디지털로31길 41', '구로구 구로동', 'cat_asian', 37.4851647, 126.8927557, NULL);
  INSERT INTO _sp VALUES ('drunkenthai', '드렁킨타이', '서울 구로구 디지털로26길 111', '구로구 구로동', 'cat_asian', 37.4825826, 126.8970445, NULL);
  INSERT INTO _sp VALUES ('rondo', '론도론도', '서울 서대문구 연희맛로 17-13', '서대문구 연희동', 'cat_bar', 37.5667778, 126.9290975, NULL);
  INSERT INTO _sp VALUES ('eoseureum', '어스름', '서울 강남구 도산대로57길 7', '강남구 청담동', 'cat_korean', 37.5241, 127.0415, NULL);
  INSERT INTO _sp VALUES ('golsu', '골수', '서울 중구 을지로3가 296-16', '중구 을지로3가', 'cat_korean', 37.5658, 126.992, NULL);
  INSERT INTO _sp VALUES ('hwadol', '화돌농장 신정점', '서울 양천구 중앙로34길 12', '양천구 신정동', 'cat_meat', 37.5192354, 126.8541449, NULL);
  INSERT INTO _sp VALUES ('kushinoa', '쿠시노아 마곡나루점', '서울 강서구 마곡중앙로 161-22', '강서구 마곡동', 'cat_pub', 37.5686437, 126.8257842, NULL);
  INSERT INTO _sp VALUES ('sodam', '소담면옥', '서울 강서구 마곡동', '강서구 마곡동', 'cat_korean', 37.568, 126.829, NULL);
  INSERT INTO _sp VALUES ('byeolmi', '별미곱창', '서울 송파구 오금로11길 11', '송파구 방이동', 'cat_meat', 37.5146536, 127.108361, NULL);
  INSERT INTO _sp VALUES ('geumdon', '금돈옥 잠실점', '서울 송파구 백제고분로 83', '송파구 잠실동', 'cat_meat', 37.5094598, 127.0793198, NULL);
  INSERT INTO _sp VALUES ('thebitnam', '더빛남', '서울 송파구 오금로18길 5', '송파구 송파동', 'cat_asian', 37.5101913, 127.1108203, NULL);
  INSERT INTO _sp VALUES ('hikiniku', '히키니쿠토코메 도산', '서울 강남구 선릉로155길 21', '강남구 신사동', 'cat_japanese', 37.5255002, 127.0379432, NULL);
  INSERT INTO _sp VALUES ('mur', '무르', '서울 강남구 테헤란로29길 8', '강남구 역삼동', 'cat_pub', 37.5021639, 127.038904, NULL);
  INSERT INTO _sp VALUES ('younghyang', '영향', '서울 금천구 남부순환로108길 7', '금천구 가산동', 'cat_western', 37.4783171, 126.8927881, NULL);
  INSERT INTO _sp VALUES ('sotnaeum', '솥내음 마곡역점', '서울 강서구 마곡중앙6로 66', '강서구 마곡동', 'cat_korean', 37.5599164, 126.834361, NULL);
  INSERT INTO _sp VALUES ('menshokatsu', '멘쇼카츠 발산역점', '서울 강서구 강서로 378', '강서구 등촌동', 'cat_japanese', 37.5592559, 126.8388443, NULL);
  INSERT INTO _sp VALUES ('sokcho', '속초그바람에 마곡점', '서울 강서구 마곡중앙6로 10', '강서구 마곡동', 'cat_seafood', 37.5603852, 126.8282142, NULL);
  INSERT INTO _sp VALUES ('hanpan', '한판승부', '서울 은평구 갈현동 403-38', '은평구 갈현동', 'cat_meat', 37.6205, 126.9127, NULL);

  UPDATE _sp SET place_id = (
    SELECT p.id FROM place p
    WHERE p.name = _sp.name
      AND ST_DWithin(p.location, ST_SetSRID(ST_MakePoint(_sp.lon, _sp.lat), 4326)::geography, 500)
    ORDER BY p.id LIMIT 1
  );

  FOR sp_row IN SELECT * FROM _sp WHERE place_id IS NULL LOOP
    INSERT INTO place (external_source, external_id, name, road_address, region_name, category_id, location)
    VALUES ('SEED_UT2', sp_row.key, sp_row.name, sp_row.road, sp_row.region, sp_row.cat,
            ST_SetSRID(ST_MakePoint(sp_row.lon, sp_row.lat), 4326)::geography)
    RETURNING id INTO v_place;
    UPDATE _sp SET place_id = v_place WHERE key = sp_row.key;
  END LOOP;

  -- ── 그룹 + 지역 태그 + persona 멤버십 ──────────────────────────
  CREATE TEMP TABLE _sg (idx int PRIMARY KEY, group_id BIGINT) ON COMMIT DROP;

  INSERT INTO groups (name, one_line_description, food_category_id, owner_id, created_at, updated_at)
  VALUES ('한국인인데 김치보다 회를 더 좋아하는 한국 사람 추천 숙성회 맛집', '숙성회에 진심인 사람들', 'cat_seafood', 902, v_group_created + interval '0 hours', v_group_created + interval '0 hours')
  RETURNING id INTO v_gid;
  INSERT INTO _sg VALUES (0, v_gid);
  INSERT INTO group_region_tag VALUES (v_gid, 'region_seoul_all');
  INSERT INTO groups (name, one_line_description, food_category_id, owner_id, created_at, updated_at)
  VALUES ('법카로 회식하기 좋은 고깃집', '구디 회식은 여기서 정합니다', 'cat_meat', 901, v_group_created + interval '1 hours', v_group_created + interval '1 hours')
  RETURNING id INTO v_gid;
  INSERT INTO _sg VALUES (1, v_gid);
  INSERT INTO group_region_tag VALUES (v_gid, 'region_guro');
  INSERT INTO groups (name, one_line_description, food_category_id, owner_id, created_at, updated_at)
  VALUES ('점심때 회사 사람들이랑 맛있는거 먹고싶을 때 가는 맛집', '구디 점심 원정대', 'cat_korean', 901, v_group_created + interval '2 hours', v_group_created + interval '2 hours')
  RETURNING id INTO v_gid;
  INSERT INTO _sg VALUES (2, v_gid);
  INSERT INTO group_region_tag VALUES (v_gid, 'region_guro');
  INSERT INTO groups (name, one_line_description, food_category_id, owner_id, created_at, updated_at)
  VALUES ('나만 알고 싶은 분위기 좋은 데이트 장소', '소개하기 아까운 곳만 모음', 'cat_bar', 903, v_group_created + interval '3 hours', v_group_created + interval '3 hours')
  RETURNING id INTO v_gid;
  INSERT INTO _sg VALUES (3, v_gid);
  INSERT INTO group_region_tag VALUES (v_gid, 'region_seoul_all');
  INSERT INTO groups (name, one_line_description, food_category_id, owner_id, created_at, updated_at)
  VALUES ('강서구에도 맛집 많다 무시하지마라', '강서구 맛집 부심', 'cat_korean', 903, v_group_created + interval '4 hours', v_group_created + interval '4 hours')
  RETURNING id INTO v_gid;
  INSERT INTO _sg VALUES (4, v_gid);
  INSERT INTO group_region_tag VALUES (v_gid, 'region_gangseo');
  INSERT INTO groups (name, one_line_description, food_category_id, owner_id, created_at, updated_at)
  VALUES ('잠실에서 놀면 맨날 여기만 가는 맛집 모음', '잠실 단골집만 모았습니다', 'cat_meat', 902, v_group_created + interval '5 hours', v_group_created + interval '5 hours')
  RETURNING id INTO v_gid;
  INSERT INTO _sg VALUES (5, v_gid);
  INSERT INTO group_region_tag VALUES (v_gid, 'region_songpa');
  INSERT INTO groups (name, one_line_description, food_category_id, owner_id, created_at, updated_at)
  VALUES ('강남에서 몇 안되는 체인점 아닌 내 맛집 모임', '강남에서 찾은 진짜 단골집', 'cat_japanese', 902, v_group_created + interval '6 hours', v_group_created + interval '6 hours')
  RETURNING id INTO v_gid;
  INSERT INTO _sg VALUES (6, v_gid);
  INSERT INTO group_region_tag VALUES (v_gid, 'region_gangnam');
  INSERT INTO groups (name, one_line_description, food_category_id, owner_id, created_at, updated_at)
  VALUES ('마곡에서 일하는 사람들을 위한 맛집 모임', '마곡 직장인 점심 원정대', 'cat_korean', 901, v_group_created + interval '7 hours', v_group_created + interval '7 hours')
  RETURNING id INTO v_gid;
  INSERT INTO _sg VALUES (7, v_gid);
  INSERT INTO group_region_tag VALUES (v_gid, 'region_gangseo');

  -- persona 3명 전원이 모든 그룹의 멤버 (mock과 동일 — 멤버 목록이 1명이면 어색하다)
  INSERT INTO group_membership (group_id, user_id, joined_at)
  SELECT g.group_id, p.uid, v_group_created + (g.idx * interval '1 hour') + interval '60 seconds'
  FROM _sg g CROSS JOIN (VALUES (901::bigint), (902::bigint), (903::bigint)) AS p(uid);

  INSERT INTO group_membership (group_id, user_id, joined_at)
  SELECT group_id, 1, v_group_created + interval '1 day' + interval '0 minutes' FROM _sg WHERE idx = 0;
  INSERT INTO group_membership (group_id, user_id, joined_at)
  SELECT group_id, 1, v_group_created + interval '1 day' + interval '1 minutes' FROM _sg WHERE idx = 1;
  INSERT INTO group_membership (group_id, user_id, joined_at)
  SELECT group_id, 1, v_group_created + interval '1 day' + interval '2 minutes' FROM _sg WHERE idx = 2;
  INSERT INTO group_membership (group_id, user_id, joined_at)
  SELECT group_id, 1, v_group_created + interval '1 day' + interval '3 minutes' FROM _sg WHERE idx = 3;
  INSERT INTO group_membership (group_id, user_id, joined_at)
  SELECT group_id, 2, v_group_created + interval '1 day' + interval '0 minutes' FROM _sg WHERE idx = 0;
  INSERT INTO group_membership (group_id, user_id, joined_at)
  SELECT group_id, 2, v_group_created + interval '1 day' + interval '1 minutes' FROM _sg WHERE idx = 1;
  INSERT INTO group_membership (group_id, user_id, joined_at)
  SELECT group_id, 2, v_group_created + interval '1 day' + interval '2 minutes' FROM _sg WHERE idx = 2;
  INSERT INTO group_membership (group_id, user_id, joined_at)
  SELECT group_id, 2, v_group_created + interval '1 day' + interval '4 minutes' FROM _sg WHERE idx = 4;
  INSERT INTO group_membership (group_id, user_id, joined_at)
  SELECT group_id, 3, v_group_created + interval '1 day' + interval '0 minutes' FROM _sg WHERE idx = 0;
  INSERT INTO group_membership (group_id, user_id, joined_at)
  SELECT group_id, 3, v_group_created + interval '1 day' + interval '1 minutes' FROM _sg WHERE idx = 1;
  INSERT INTO group_membership (group_id, user_id, joined_at)
  SELECT group_id, 3, v_group_created + interval '1 day' + interval '2 minutes' FROM _sg WHERE idx = 2;
  INSERT INTO group_membership (group_id, user_id, joined_at)
  SELECT group_id, 3, v_group_created + interval '1 day' + interval '5 minutes' FROM _sg WHERE idx = 5;
  INSERT INTO group_membership (group_id, user_id, joined_at)
  SELECT group_id, 4, v_group_created + interval '1 day' + interval '0 minutes' FROM _sg WHERE idx = 0;
  INSERT INTO group_membership (group_id, user_id, joined_at)
  SELECT group_id, 4, v_group_created + interval '1 day' + interval '1 minutes' FROM _sg WHERE idx = 1;
  INSERT INTO group_membership (group_id, user_id, joined_at)
  SELECT group_id, 4, v_group_created + interval '1 day' + interval '2 minutes' FROM _sg WHERE idx = 2;
  INSERT INTO group_membership (group_id, user_id, joined_at)
  SELECT group_id, 4, v_group_created + interval '1 day' + interval '6 minutes' FROM _sg WHERE idx = 6;

  -- ── 리뷰: save → 사진 → 태그 → review → AI 요약 → 티켓 → 공유 ──

  -- [0] sasanoha
  v_at := v_reviewed + interval '0 hours';
  SELECT place_id INTO v_place FROM _sp WHERE key = 'sasanoha';
  INSERT INTO save (user_id, place_id, rating, content, created_at, updated_at)
  VALUES (902, v_place, 5, '근처 술집 중에서도 유독 리뷰가 많고 별점이 높아서 석촌호수 가게 되면 들려야지 하고 찜 해놓았다가 가봤는데 웬걸... 인생 맛집이 될 줄이야... 가격도 싸고 이것저것 종류별로 시킬 수 있어서 너무 좋아요. 일본인도 방문할 정도로 제대로 된 이자카야 집입니다~ 특히 양맥이랑 같이 먹는 숙성회가 짱!! 근데 갈때마다 웨이팅이 사악해서 가기 2시간 전에 꼭 캐치테이블로 미리 웨이팅 걸어놓고 가세요… 서서 먹으면 앉아서 먹는거 보다는 빨리 들어갈 수 있음. 그리고 전부 닷지 테이블이라서 단체 방문은 불가능ㅠㅠ', v_at, v_at) RETURNING id INTO v_save;
  INSERT INTO save_tag (save_id, tag_id) SELECT v_save, unnest(ARRAY['tag_friend','tag_tasty','tag_value']::text[]);
  INSERT INTO media_asset (owner_id, s3_key, content_type, content_length, status, created_at, attached_at)
  VALUES (902, 'seed/ut2/g6-sasanoha/01.jpg', 'image/jpeg', 420781, 'ATTACHED', v_at, v_at)
  ON CONFLICT (s3_key) DO NOTHING;
  SELECT id INTO v_asset FROM media_asset WHERE s3_key = 'seed/ut2/g6-sasanoha/01.jpg';
  INSERT INTO save_photo (save_id, media_asset_id, photo_order) VALUES (v_save, v_asset, 0);
  INSERT INTO media_asset (owner_id, s3_key, content_type, content_length, status, created_at, attached_at)
  VALUES (902, 'seed/ut2/g6-sasanoha/02.jpg', 'image/jpeg', 501799, 'ATTACHED', v_at, v_at)
  ON CONFLICT (s3_key) DO NOTHING;
  SELECT id INTO v_asset FROM media_asset WHERE s3_key = 'seed/ut2/g6-sasanoha/02.jpg';
  INSERT INTO save_photo (save_id, media_asset_id, photo_order) VALUES (v_save, v_asset, 1);
  INSERT INTO media_asset (owner_id, s3_key, content_type, content_length, status, created_at, attached_at)
  VALUES (902, 'seed/ut2/g6-sasanoha/03.jpg', 'image/jpeg', 366498, 'ATTACHED', v_at, v_at)
  ON CONFLICT (s3_key) DO NOTHING;
  SELECT id INTO v_asset FROM media_asset WHERE s3_key = 'seed/ut2/g6-sasanoha/03.jpg';
  INSERT INTO save_photo (save_id, media_asset_id, photo_order) VALUES (v_save, v_asset, 2);
  INSERT INTO review (save_id, user_id, place_id, created_at)
  VALUES (v_save, 902, v_place, v_at) RETURNING id INTO v_review;
  INSERT INTO review_ai_summary (review_id, pros, cons, model, created_at)
  VALUES (v_review, '숙성회가 신선하고 가성비가 좋아요', '웨이팅이 길고 단체 방문은 어려워요', 'seed-human', v_at);
  INSERT INTO reward_grant (user_id, reward_type, source_type, source_id, created_at)
  VALUES (902, 'GROUP_JOIN_TICKET', 'REVIEW', v_review, v_at) RETURNING id INTO v_grant;
  INSERT INTO group_join_ticket (user_id, reward_grant_id, status, created_at)
  VALUES (902, v_grant, 'AVAILABLE', v_at);
  INSERT INTO group_review_share (group_id, review_id, user_id, created_at)
  SELECT group_id, v_review, 902, v_at FROM _sg WHERE idx = 0;
  INSERT INTO group_review_share (group_id, review_id, user_id, created_at)
  SELECT group_id, v_review, 902, v_at FROM _sg WHERE idx = 5;

  -- [1] dorimhang
  v_at := v_reviewed + interval '2 hours';
  SELECT place_id INTO v_place FROM _sp WHERE key = 'dorimhang';
  INSERT INTO save (user_id, place_id, rating, content, created_at, updated_at)
  VALUES (903, v_place, 5, '구로동, 신림동에서 손꼽히는 이자카야✨ 서울에서도 몇 안 되는 최고의 맛집이라 자부할 수 있어요. 생일날 남자친구와 방문했는데 정말 최고의 선택이었습니다! 다만 평일에도 웨이팅 심하니까 꼭 어플로 예약 후 방문하시길 추천드려요:)', v_at, v_at) RETURNING id INTO v_save;
  INSERT INTO save_tag (save_id, tag_id) SELECT v_save, unnest(ARRAY['tag_couple','tag_tasty','tag_mood']::text[]);
  INSERT INTO media_asset (owner_id, s3_key, content_type, content_length, status, created_at, attached_at)
  VALUES (903, 'seed/ut2/g1-dorimhang/01.jpg', 'image/jpeg', 418353, 'ATTACHED', v_at, v_at)
  ON CONFLICT (s3_key) DO NOTHING;
  SELECT id INTO v_asset FROM media_asset WHERE s3_key = 'seed/ut2/g1-dorimhang/01.jpg';
  INSERT INTO save_photo (save_id, media_asset_id, photo_order) VALUES (v_save, v_asset, 0);
  INSERT INTO media_asset (owner_id, s3_key, content_type, content_length, status, created_at, attached_at)
  VALUES (903, 'seed/ut2/g1-dorimhang/02.jpg', 'image/jpeg', 198040, 'ATTACHED', v_at, v_at)
  ON CONFLICT (s3_key) DO NOTHING;
  SELECT id INTO v_asset FROM media_asset WHERE s3_key = 'seed/ut2/g1-dorimhang/02.jpg';
  INSERT INTO save_photo (save_id, media_asset_id, photo_order) VALUES (v_save, v_asset, 1);
  INSERT INTO media_asset (owner_id, s3_key, content_type, content_length, status, created_at, attached_at)
  VALUES (903, 'seed/ut2/g1-dorimhang/03.jpg', 'image/jpeg', 430052, 'ATTACHED', v_at, v_at)
  ON CONFLICT (s3_key) DO NOTHING;
  SELECT id INTO v_asset FROM media_asset WHERE s3_key = 'seed/ut2/g1-dorimhang/03.jpg';
  INSERT INTO save_photo (save_id, media_asset_id, photo_order) VALUES (v_save, v_asset, 2);
  INSERT INTO review (save_id, user_id, place_id, created_at)
  VALUES (v_save, 903, v_place, v_at) RETURNING id INTO v_review;
  INSERT INTO review_ai_summary (review_id, pros, cons, model, created_at)
  VALUES (v_review, '분위기가 좋고 특별한 날에 어울려요', '평일에도 웨이팅이 있어 예약이 필요해요', 'seed-human', v_at);
  INSERT INTO reward_grant (user_id, reward_type, source_type, source_id, created_at)
  VALUES (903, 'GROUP_JOIN_TICKET', 'REVIEW', v_review, v_at) RETURNING id INTO v_grant;
  INSERT INTO group_join_ticket (user_id, reward_grant_id, status, created_at)
  VALUES (903, v_grant, 'AVAILABLE', v_at);
  INSERT INTO group_review_share (group_id, review_id, user_id, created_at)
  SELECT group_id, v_review, 903, v_at FROM _sg WHERE idx = 0;

  -- [2] hanpan
  v_at := v_reviewed + interval '4 hours';
  SELECT place_id INTO v_place FROM _sp WHERE key = 'hanpan';
  INSERT INTO save (user_id, place_id, rating, content, created_at, updated_at)
  VALUES (901, v_place, 5, '이 집의 단점은 가격대가 조금 높다는 점이에요. 하지만 한 번도 안 가본 사람은 있어도 한 번만 간 사람은 없을 만큼 중독적인 맛집입니다. 저희는 너무 맛있어서 둘이서 메인 메뉴 세 가지나 시켜 먹은 적도 있고 한 번은 1차에서 회랑 파스타 먹고 2차로 옆자리로 옮겨서 3명에서 후토마키와 탕까지 먹은 적도 있어요. 그만큼 진심으로 추천할 수 있는 집입니다🙌 근데 최대 수용 인원이 3명까지인게 단점ㅠㅠ', v_at, v_at) RETURNING id INTO v_save;
  INSERT INTO save_tag (save_id, tag_id) SELECT v_save, unnest(ARRAY['tag_friend','tag_tasty']::text[]);
  INSERT INTO media_asset (owner_id, s3_key, content_type, content_length, status, created_at, attached_at)
  VALUES (901, 'seed/ut2/g1-hanpan/01.jpg', 'image/jpeg', 299169, 'ATTACHED', v_at, v_at)
  ON CONFLICT (s3_key) DO NOTHING;
  SELECT id INTO v_asset FROM media_asset WHERE s3_key = 'seed/ut2/g1-hanpan/01.jpg';
  INSERT INTO save_photo (save_id, media_asset_id, photo_order) VALUES (v_save, v_asset, 0);
  INSERT INTO media_asset (owner_id, s3_key, content_type, content_length, status, created_at, attached_at)
  VALUES (901, 'seed/ut2/g1-hanpan/02.jpg', 'image/jpeg', 613479, 'ATTACHED', v_at, v_at)
  ON CONFLICT (s3_key) DO NOTHING;
  SELECT id INTO v_asset FROM media_asset WHERE s3_key = 'seed/ut2/g1-hanpan/02.jpg';
  INSERT INTO save_photo (save_id, media_asset_id, photo_order) VALUES (v_save, v_asset, 1);
  INSERT INTO media_asset (owner_id, s3_key, content_type, content_length, status, created_at, attached_at)
  VALUES (901, 'seed/ut2/g1-hanpan/03.jpg', 'image/jpeg', 577586, 'ATTACHED', v_at, v_at)
  ON CONFLICT (s3_key) DO NOTHING;
  SELECT id INTO v_asset FROM media_asset WHERE s3_key = 'seed/ut2/g1-hanpan/03.jpg';
  INSERT INTO save_photo (save_id, media_asset_id, photo_order) VALUES (v_save, v_asset, 2);
  INSERT INTO review (save_id, user_id, place_id, created_at)
  VALUES (v_save, 901, v_place, v_at) RETURNING id INTO v_review;
  INSERT INTO review_ai_summary (review_id, pros, cons, model, created_at)
  VALUES (v_review, '메뉴 구성이 다양하고 중독성 있는 맛이에요', '가격대가 높고 최대 3명까지만 갈 수 있어요', 'seed-human', v_at);
  INSERT INTO reward_grant (user_id, reward_type, source_type, source_id, created_at)
  VALUES (901, 'GROUP_JOIN_TICKET', 'REVIEW', v_review, v_at) RETURNING id INTO v_grant;
  INSERT INTO group_join_ticket (user_id, reward_grant_id, status, created_at)
  VALUES (901, v_grant, 'AVAILABLE', v_at);
  INSERT INTO group_review_share (group_id, review_id, user_id, created_at)
  SELECT group_id, v_review, 901, v_at FROM _sg WHERE idx = 0;

  -- [3] mukeunji
  v_at := v_reviewed + interval '6 hours';
  SELECT place_id INTO v_place FROM _sp WHERE key = 'mukeunji';
  INSERT INTO save (user_id, place_id, rating, content, created_at, updated_at)
  VALUES (901, v_place, 4, '친구가 야장 삼겹살이 먹고 싶다고 해서 마침 회사 동료가 추천해준 맛집이 떠올라 가보았어요. 묵은지가 킥이었고 요즘 같은 날씨에 즐기는 야장은 그야말로 행복입니다:) 근데 가격이 굉장히 사악하긴 함. 삼겹살도 일반 냉동 삼겹…', v_at, v_at) RETURNING id INTO v_save;
  INSERT INTO save_tag (save_id, tag_id) SELECT v_save, unnest(ARRAY['tag_friend','tag_mood']::text[]);
  INSERT INTO media_asset (owner_id, s3_key, content_type, content_length, status, created_at, attached_at)
  VALUES (901, 'seed/ut2/g2-mukeunji/01.jpg', 'image/jpeg', 442966, 'ATTACHED', v_at, v_at)
  ON CONFLICT (s3_key) DO NOTHING;
  SELECT id INTO v_asset FROM media_asset WHERE s3_key = 'seed/ut2/g2-mukeunji/01.jpg';
  INSERT INTO save_photo (save_id, media_asset_id, photo_order) VALUES (v_save, v_asset, 0);
  INSERT INTO media_asset (owner_id, s3_key, content_type, content_length, status, created_at, attached_at)
  VALUES (901, 'seed/ut2/g2-mukeunji/02.jpg', 'image/jpeg', 424612, 'ATTACHED', v_at, v_at)
  ON CONFLICT (s3_key) DO NOTHING;
  SELECT id INTO v_asset FROM media_asset WHERE s3_key = 'seed/ut2/g2-mukeunji/02.jpg';
  INSERT INTO save_photo (save_id, media_asset_id, photo_order) VALUES (v_save, v_asset, 1);
  INSERT INTO media_asset (owner_id, s3_key, content_type, content_length, status, created_at, attached_at)
  VALUES (901, 'seed/ut2/g2-mukeunji/03.jpg', 'image/jpeg', 553707, 'ATTACHED', v_at, v_at)
  ON CONFLICT (s3_key) DO NOTHING;
  SELECT id INTO v_asset FROM media_asset WHERE s3_key = 'seed/ut2/g2-mukeunji/03.jpg';
  INSERT INTO save_photo (save_id, media_asset_id, photo_order) VALUES (v_save, v_asset, 2);
  INSERT INTO review (save_id, user_id, place_id, created_at)
  VALUES (v_save, 901, v_place, v_at) RETURNING id INTO v_review;
  INSERT INTO review_ai_summary (review_id, pros, cons, model, created_at)
  VALUES (v_review, '묵은지 조합과 야장 분위기가 좋아요', '가격이 높은 편이에요', 'seed-human', v_at);
  INSERT INTO reward_grant (user_id, reward_type, source_type, source_id, created_at)
  VALUES (901, 'GROUP_JOIN_TICKET', 'REVIEW', v_review, v_at) RETURNING id INTO v_grant;
  INSERT INTO group_join_ticket (user_id, reward_grant_id, status, created_at)
  VALUES (901, v_grant, 'AVAILABLE', v_at);
  INSERT INTO group_review_share (group_id, review_id, user_id, created_at)
  SELECT group_id, v_review, 901, v_at FROM _sg WHERE idx = 1;

  -- [4] keunjip
  v_at := v_reviewed + interval '8 hours';
  SELECT place_id INTO v_place FROM _sp WHERE key = 'keunjip';
  INSERT INTO save (user_id, place_id, rating, content, created_at, updated_at)
  VALUES (901, v_place, 5, '회사 회식으로 간 삼겹살집... 가게는 허름한데 고기가 1인분에 16,000원 하길래 좀 비싼 거 같기도... 했지만 전혀 1도 돈이 아깝다는 생각이 들지 않았음 지금까지 내가 가본 삼겹살집 중에 제일 두툼하고 맛있었음ㅠㅠ 정말 사장님 맛잘알인게 메뉴에 미나리 추가도 있었고 분명 삼겹살 시켰는데 묵은지도 같이 주심 김치 러버는 죽어ㅎㅎ 그리고는 두번째로 가브리살을 시켰는데 응..? 우리 목살시켰나..? 생맥주는 또 왜 이렇게 싼 건지..! 생맥주는 3,000원이고 스텔라 생맥주는 5,000원..! 여기 계란찜에는 새우도 넣어서 주심... 마지막 입가심으로 먹은 차돌박이까지 모든 메뉴가 다 100% 만족스러웠던 집ㅠㅠ 다음에 가면 다른 메뉴들도 진짜 다 뿌순다..!', v_at, v_at) RETURNING id INTO v_save;
  INSERT INTO save_tag (save_id, tag_id) SELECT v_save, unnest(ARRAY['tag_colleague','tag_tasty','tag_value']::text[]);
  INSERT INTO media_asset (owner_id, s3_key, content_type, content_length, status, created_at, attached_at)
  VALUES (901, 'seed/ut2/g2-keunjip/01.jpg', 'image/jpeg', 432362, 'ATTACHED', v_at, v_at)
  ON CONFLICT (s3_key) DO NOTHING;
  SELECT id INTO v_asset FROM media_asset WHERE s3_key = 'seed/ut2/g2-keunjip/01.jpg';
  INSERT INTO save_photo (save_id, media_asset_id, photo_order) VALUES (v_save, v_asset, 0);
  INSERT INTO media_asset (owner_id, s3_key, content_type, content_length, status, created_at, attached_at)
  VALUES (901, 'seed/ut2/g2-keunjip/02.jpg', 'image/jpeg', 321567, 'ATTACHED', v_at, v_at)
  ON CONFLICT (s3_key) DO NOTHING;
  SELECT id INTO v_asset FROM media_asset WHERE s3_key = 'seed/ut2/g2-keunjip/02.jpg';
  INSERT INTO save_photo (save_id, media_asset_id, photo_order) VALUES (v_save, v_asset, 1);
  INSERT INTO media_asset (owner_id, s3_key, content_type, content_length, status, created_at, attached_at)
  VALUES (901, 'seed/ut2/g2-keunjip/03.jpg', 'image/jpeg', 657440, 'ATTACHED', v_at, v_at)
  ON CONFLICT (s3_key) DO NOTHING;
  SELECT id INTO v_asset FROM media_asset WHERE s3_key = 'seed/ut2/g2-keunjip/03.jpg';
  INSERT INTO save_photo (save_id, media_asset_id, photo_order) VALUES (v_save, v_asset, 2);
  INSERT INTO review (save_id, user_id, place_id, created_at)
  VALUES (v_save, 901, v_place, v_at) RETURNING id INTO v_review;
  INSERT INTO review_ai_summary (review_id, pros, cons, model, created_at)
  VALUES (v_review, '고기가 두툼하고 생맥주가 저렴해요', '회식 테이블이 떨어져 있을 수 있어요', 'seed-human', v_at);
  INSERT INTO reward_grant (user_id, reward_type, source_type, source_id, created_at)
  VALUES (901, 'GROUP_JOIN_TICKET', 'REVIEW', v_review, v_at) RETURNING id INTO v_grant;
  INSERT INTO group_join_ticket (user_id, reward_grant_id, status, created_at)
  VALUES (901, v_grant, 'AVAILABLE', v_at);
  INSERT INTO group_review_share (group_id, review_id, user_id, created_at)
  SELECT group_id, v_review, 901, v_at FROM _sg WHERE idx = 1;

  -- [5] malttuk
  v_at := v_reviewed + interval '10 hours';
  SELECT place_id INTO v_place FROM _sp WHERE key = 'malttuk';
  INSERT INTO save (user_id, place_id, rating, content, created_at, updated_at)
  VALUES (902, v_place, 4, '가끔 그런 날이 있잖아요... 폭력적이고, 입안 가득 기름진 음식을 먹고 싶은 그런 날... 구디에서 유명한 말뚝곱창. 구디에 지점이 제일 많이 있는 데는 이유가 있습니다ㅎㅎ 저는 떡을 별로 안 좋아하는데 생각보다 떡이 너무 부드럽고 맛있었어요! 추가는 절대 안하는 사람인데 이날은 떡 추가해서 먹었습니다. 근데 아무래도 소곱창이라 매장에 기름이 많고 테이블에 기름이 많았습니다. 매장 깨끗한거 선호하시는 분은 약간 그럴수도…', v_at, v_at) RETURNING id INTO v_save;
  INSERT INTO save_tag (save_id, tag_id) SELECT v_save, unnest(ARRAY['tag_colleague','tag_tasty']::text[]);
  INSERT INTO media_asset (owner_id, s3_key, content_type, content_length, status, created_at, attached_at)
  VALUES (902, 'seed/ut2/g2-malttuk/01.jpg', 'image/jpeg', 469136, 'ATTACHED', v_at, v_at)
  ON CONFLICT (s3_key) DO NOTHING;
  SELECT id INTO v_asset FROM media_asset WHERE s3_key = 'seed/ut2/g2-malttuk/01.jpg';
  INSERT INTO save_photo (save_id, media_asset_id, photo_order) VALUES (v_save, v_asset, 0);
  INSERT INTO media_asset (owner_id, s3_key, content_type, content_length, status, created_at, attached_at)
  VALUES (902, 'seed/ut2/g2-malttuk/02.jpg', 'image/jpeg', 227608, 'ATTACHED', v_at, v_at)
  ON CONFLICT (s3_key) DO NOTHING;
  SELECT id INTO v_asset FROM media_asset WHERE s3_key = 'seed/ut2/g2-malttuk/02.jpg';
  INSERT INTO save_photo (save_id, media_asset_id, photo_order) VALUES (v_save, v_asset, 1);
  INSERT INTO media_asset (owner_id, s3_key, content_type, content_length, status, created_at, attached_at)
  VALUES (902, 'seed/ut2/g2-malttuk/03.jpg', 'image/jpeg', 405449, 'ATTACHED', v_at, v_at)
  ON CONFLICT (s3_key) DO NOTHING;
  SELECT id INTO v_asset FROM media_asset WHERE s3_key = 'seed/ut2/g2-malttuk/03.jpg';
  INSERT INTO save_photo (save_id, media_asset_id, photo_order) VALUES (v_save, v_asset, 2);
  INSERT INTO review (save_id, user_id, place_id, created_at)
  VALUES (v_save, 902, v_place, v_at) RETURNING id INTO v_review;
  INSERT INTO review_ai_summary (review_id, pros, cons, model, created_at)
  VALUES (v_review, '곱창이 부드럽고 떡 추가가 맛있어요', '매장과 테이블에 기름기가 많아요', 'seed-human', v_at);
  INSERT INTO reward_grant (user_id, reward_type, source_type, source_id, created_at)
  VALUES (902, 'GROUP_JOIN_TICKET', 'REVIEW', v_review, v_at) RETURNING id INTO v_grant;
  INSERT INTO group_join_ticket (user_id, reward_grant_id, status, created_at)
  VALUES (902, v_grant, 'AVAILABLE', v_at);
  INSERT INTO group_review_share (group_id, review_id, user_id, created_at)
  SELECT group_id, v_review, 902, v_at FROM _sg WHERE idx = 1;

  -- [6] niwasushi
  v_at := v_reviewed + interval '12 hours';
  SELECT place_id INTO v_place FROM _sp WHERE key = 'niwasushi';
  INSERT INTO save (user_id, place_id, rating, content, created_at, updated_at)
  VALUES (901, v_place, 4, '초밥집 많이 가봤는데 나쁘지 않음. 회도 두툼하고 맛도 있음. 구로에서 초밥 먹고싶으면 내가 가본 곳 중에 제일 추천. 점심 메뉴 시키면 우동이랑 튀김도 같이 나옴. 근데 남자분들이 가면 양이 좀 적을수도 있음.', v_at, v_at) RETURNING id INTO v_save;
  INSERT INTO save_tag (save_id, tag_id) SELECT v_save, unnest(ARRAY['tag_colleague','tag_value']::text[]);
  INSERT INTO media_asset (owner_id, s3_key, content_type, content_length, status, created_at, attached_at)
  VALUES (901, 'seed/ut2/g3-niwasushi/01.jpg', 'image/jpeg', 372463, 'ATTACHED', v_at, v_at)
  ON CONFLICT (s3_key) DO NOTHING;
  SELECT id INTO v_asset FROM media_asset WHERE s3_key = 'seed/ut2/g3-niwasushi/01.jpg';
  INSERT INTO save_photo (save_id, media_asset_id, photo_order) VALUES (v_save, v_asset, 0);
  INSERT INTO review (save_id, user_id, place_id, created_at)
  VALUES (v_save, 901, v_place, v_at) RETURNING id INTO v_review;
  INSERT INTO review_ai_summary (review_id, pros, cons, model, created_at)
  VALUES (v_review, '회가 두툼하고 점심 구성이 알차요', '양이 적게 느껴질 수 있어요', 'seed-human', v_at);
  INSERT INTO reward_grant (user_id, reward_type, source_type, source_id, created_at)
  VALUES (901, 'GROUP_JOIN_TICKET', 'REVIEW', v_review, v_at) RETURNING id INTO v_grant;
  INSERT INTO group_join_ticket (user_id, reward_grant_id, status, created_at)
  VALUES (901, v_grant, 'AVAILABLE', v_at);
  INSERT INTO group_review_share (group_id, review_id, user_id, created_at)
  SELECT group_id, v_review, 901, v_at FROM _sg WHERE idx = 2;

  -- [7] ohansu
  v_at := v_reviewed + interval '14 hours';
  SELECT place_id INTO v_place FROM _sp WHERE key = 'ohansu';
  INSERT INTO save (user_id, place_id, rating, content, created_at, updated_at)
  VALUES (903, v_place, 4, '우육탕면 먹으러 왔는데 확실히 실패 없는 맛. 국물이 갈비탕 육수랑 비슷한 느낌인데 훨씬 진하고 감칠맛남. 군만두도 많이 말고 맛만 볼 수 있을 정도로 시킬 수 있음. 파랑 고수도 무료. 근데 내가 갔을 때는 서비스가 좀 아쉬웠음.', v_at, v_at) RETURNING id INTO v_save;
  INSERT INTO save_tag (save_id, tag_id) SELECT v_save, unnest(ARRAY['tag_colleague','tag_tasty']::text[]);
  INSERT INTO media_asset (owner_id, s3_key, content_type, content_length, status, created_at, attached_at)
  VALUES (903, 'seed/ut2/g3-ohansu/01.jpg', 'image/jpeg', 462277, 'ATTACHED', v_at, v_at)
  ON CONFLICT (s3_key) DO NOTHING;
  SELECT id INTO v_asset FROM media_asset WHERE s3_key = 'seed/ut2/g3-ohansu/01.jpg';
  INSERT INTO save_photo (save_id, media_asset_id, photo_order) VALUES (v_save, v_asset, 0);
  INSERT INTO review (save_id, user_id, place_id, created_at)
  VALUES (v_save, 903, v_place, v_at) RETURNING id INTO v_review;
  INSERT INTO review_ai_summary (review_id, pros, cons, model, created_at)
  VALUES (v_review, '국물이 진하고 감칠맛이 좋아요', '서비스가 아쉬울 때가 있어요', 'seed-human', v_at);
  INSERT INTO reward_grant (user_id, reward_type, source_type, source_id, created_at)
  VALUES (903, 'GROUP_JOIN_TICKET', 'REVIEW', v_review, v_at) RETURNING id INTO v_grant;
  INSERT INTO group_join_ticket (user_id, reward_grant_id, status, created_at)
  VALUES (903, v_grant, 'AVAILABLE', v_at);
  INSERT INTO group_review_share (group_id, review_id, user_id, created_at)
  SELECT group_id, v_review, 903, v_at FROM _sg WHERE idx = 2;

  -- [8] drunkenthai
  v_at := v_reviewed + interval '16 hours';
  SELECT place_id INTO v_place FROM _sp WHERE key = 'drunkenthai';
  INSERT INTO save (user_id, place_id, rating, content, created_at, updated_at)
  VALUES (901, v_place, 4, '새콤한 맛과 동남아 커리의 고소하고 부드럽게 퍼지는 질감을 맛보기 좋은 곳. 애매하게 로컬화되지 않은 맛. 커리 먹으러 다시 가고 싶은 곳. 근데 11시 20분쯤 안가면 자리가 없어서 못 먹을수도 있음. 구로에서 제일 맛있는 가게 탑 5안에 듬. 매장이 약간 작아서 회식이나 단체로 가기는 좀 애매함.', v_at, v_at) RETURNING id INTO v_save;
  INSERT INTO save_tag (save_id, tag_id) SELECT v_save, unnest(ARRAY['tag_colleague','tag_tasty']::text[]);
  INSERT INTO media_asset (owner_id, s3_key, content_type, content_length, status, created_at, attached_at)
  VALUES (901, 'seed/ut2/g3-drunkenthai/01.jpg', 'image/jpeg', 408188, 'ATTACHED', v_at, v_at)
  ON CONFLICT (s3_key) DO NOTHING;
  SELECT id INTO v_asset FROM media_asset WHERE s3_key = 'seed/ut2/g3-drunkenthai/01.jpg';
  INSERT INTO save_photo (save_id, media_asset_id, photo_order) VALUES (v_save, v_asset, 0);
  INSERT INTO review (save_id, user_id, place_id, created_at)
  VALUES (v_save, 901, v_place, v_at) RETURNING id INTO v_review;
  INSERT INTO review_ai_summary (review_id, pros, cons, model, created_at)
  VALUES (v_review, '로컬화되지 않은 진한 커리 맛이에요', '매장이 작고 늦으면 자리가 없어요', 'seed-human', v_at);
  INSERT INTO reward_grant (user_id, reward_type, source_type, source_id, created_at)
  VALUES (901, 'GROUP_JOIN_TICKET', 'REVIEW', v_review, v_at) RETURNING id INTO v_grant;
  INSERT INTO group_join_ticket (user_id, reward_grant_id, status, created_at)
  VALUES (901, v_grant, 'AVAILABLE', v_at);
  INSERT INTO group_review_share (group_id, review_id, user_id, created_at)
  SELECT group_id, v_review, 901, v_at FROM _sg WHERE idx = 2;

  -- [9] rondo
  v_at := v_reviewed + interval '18 hours';
  SELECT place_id INTO v_place FROM _sp WHERE key = 'rondo';
  INSERT INTO save (user_id, place_id, rating, content, created_at, updated_at)
  VALUES (903, v_place, 5, '남자친구 생일에 지인들과 함께 방문했어요! 와인에 막 입문했을 때였는데 가격도 합리적이고 안주도 맛있어서 와인 초보자에게 딱 좋은 곳이었습니다. 다만 메뉴 양이 조금 적어서 여러 가지를 함께 주문해야 했어요:)', v_at, v_at) RETURNING id INTO v_save;
  INSERT INTO save_tag (save_id, tag_id) SELECT v_save, unnest(ARRAY['tag_couple','tag_mood','tag_value']::text[]);
  INSERT INTO media_asset (owner_id, s3_key, content_type, content_length, status, created_at, attached_at)
  VALUES (903, 'seed/ut2/g4-rondo/01.jpg', 'image/jpeg', 511877, 'ATTACHED', v_at, v_at)
  ON CONFLICT (s3_key) DO NOTHING;
  SELECT id INTO v_asset FROM media_asset WHERE s3_key = 'seed/ut2/g4-rondo/01.jpg';
  INSERT INTO save_photo (save_id, media_asset_id, photo_order) VALUES (v_save, v_asset, 0);
  INSERT INTO media_asset (owner_id, s3_key, content_type, content_length, status, created_at, attached_at)
  VALUES (903, 'seed/ut2/g4-rondo/02.jpg', 'image/jpeg', 331964, 'ATTACHED', v_at, v_at)
  ON CONFLICT (s3_key) DO NOTHING;
  SELECT id INTO v_asset FROM media_asset WHERE s3_key = 'seed/ut2/g4-rondo/02.jpg';
  INSERT INTO save_photo (save_id, media_asset_id, photo_order) VALUES (v_save, v_asset, 1);
  INSERT INTO media_asset (owner_id, s3_key, content_type, content_length, status, created_at, attached_at)
  VALUES (903, 'seed/ut2/g4-rondo/03.jpg', 'image/jpeg', 622772, 'ATTACHED', v_at, v_at)
  ON CONFLICT (s3_key) DO NOTHING;
  SELECT id INTO v_asset FROM media_asset WHERE s3_key = 'seed/ut2/g4-rondo/03.jpg';
  INSERT INTO save_photo (save_id, media_asset_id, photo_order) VALUES (v_save, v_asset, 2);
  INSERT INTO review (save_id, user_id, place_id, created_at)
  VALUES (v_save, 903, v_place, v_at) RETURNING id INTO v_review;
  INSERT INTO review_ai_summary (review_id, pros, cons, model, created_at)
  VALUES (v_review, '와인 입문자에게 좋고 가격이 합리적이에요', '메뉴 양이 적은 편이에요', 'seed-human', v_at);
  INSERT INTO reward_grant (user_id, reward_type, source_type, source_id, created_at)
  VALUES (903, 'GROUP_JOIN_TICKET', 'REVIEW', v_review, v_at) RETURNING id INTO v_grant;
  INSERT INTO group_join_ticket (user_id, reward_grant_id, status, created_at)
  VALUES (903, v_grant, 'AVAILABLE', v_at);
  INSERT INTO group_review_share (group_id, review_id, user_id, created_at)
  SELECT group_id, v_review, 903, v_at FROM _sg WHERE idx = 3;

  -- [10] eoseureum
  v_at := v_reviewed + interval '20 hours';
  SELECT place_id INTO v_place FROM _sp WHERE key = 'eoseureum';
  INSERT INTO save (user_id, place_id, rating, content, created_at, updated_at)
  VALUES (902, v_place, 5, '크리스마스날 남자친구와 방문했어요! 이번에는 코스로 즐겼는데 다음에는 와인이랑 단품 메뉴를 따로 시켜보고 싶었어요. 매장이 한식을 재해석한 다이닝바라서 코스 메뉴가 계절마다 바뀌는 점이 인상적이었어요. 메뉴들 양이 좀 적어서 코스는 양이 딱 적당했지만 단품으로 먹는다면 조금 아쉬울 수도 있을 것 같아요:)', v_at, v_at) RETURNING id INTO v_save;
  INSERT INTO save_tag (save_id, tag_id) SELECT v_save, unnest(ARRAY['tag_couple','tag_mood']::text[]);
  INSERT INTO media_asset (owner_id, s3_key, content_type, content_length, status, created_at, attached_at)
  VALUES (902, 'seed/ut2/g4-eoseureum/01.jpg', 'image/jpeg', 298106, 'ATTACHED', v_at, v_at)
  ON CONFLICT (s3_key) DO NOTHING;
  SELECT id INTO v_asset FROM media_asset WHERE s3_key = 'seed/ut2/g4-eoseureum/01.jpg';
  INSERT INTO save_photo (save_id, media_asset_id, photo_order) VALUES (v_save, v_asset, 0);
  INSERT INTO media_asset (owner_id, s3_key, content_type, content_length, status, created_at, attached_at)
  VALUES (902, 'seed/ut2/g4-eoseureum/02.jpg', 'image/jpeg', 294127, 'ATTACHED', v_at, v_at)
  ON CONFLICT (s3_key) DO NOTHING;
  SELECT id INTO v_asset FROM media_asset WHERE s3_key = 'seed/ut2/g4-eoseureum/02.jpg';
  INSERT INTO save_photo (save_id, media_asset_id, photo_order) VALUES (v_save, v_asset, 1);
  INSERT INTO media_asset (owner_id, s3_key, content_type, content_length, status, created_at, attached_at)
  VALUES (902, 'seed/ut2/g4-eoseureum/03.jpg', 'image/jpeg', 495671, 'ATTACHED', v_at, v_at)
  ON CONFLICT (s3_key) DO NOTHING;
  SELECT id INTO v_asset FROM media_asset WHERE s3_key = 'seed/ut2/g4-eoseureum/03.jpg';
  INSERT INTO save_photo (save_id, media_asset_id, photo_order) VALUES (v_save, v_asset, 2);
  INSERT INTO review (save_id, user_id, place_id, created_at)
  VALUES (v_save, 902, v_place, v_at) RETURNING id INTO v_review;
  INSERT INTO review_ai_summary (review_id, pros, cons, model, created_at)
  VALUES (v_review, '계절마다 바뀌는 한식 다이닝 코스가 인상적이에요', '단품은 양이 아쉬울 수 있어요', 'seed-human', v_at);
  INSERT INTO reward_grant (user_id, reward_type, source_type, source_id, created_at)
  VALUES (902, 'GROUP_JOIN_TICKET', 'REVIEW', v_review, v_at) RETURNING id INTO v_grant;
  INSERT INTO group_join_ticket (user_id, reward_grant_id, status, created_at)
  VALUES (902, v_grant, 'AVAILABLE', v_at);
  INSERT INTO group_review_share (group_id, review_id, user_id, created_at)
  SELECT group_id, v_review, 902, v_at FROM _sg WHERE idx = 3;

  -- [11] golsu
  v_at := v_reviewed + interval '22 hours';
  SELECT place_id INTO v_place FROM _sp WHERE key = 'golsu';
  INSERT INTO save (user_id, place_id, rating, content, created_at, updated_at)
  VALUES (903, v_place, 5, '인스타 맛집은 잘 안믿는 1인 ....
근데 여긴 진짜 쥐림 .. 에바임 진짜 에바임
일부러 인스타에 없는 맛집들 찾아가려고 굉장히 애쓰는 편인데 수제비에 소주가 너무 좋아보여서 감. 사실 첫인상이 생각보다 양이 막 많고 맛있는 거는 아닌데,
수육 끝나고 먹은 수제비가 ㄹㅇ 지렸음 ... 1시간안에 여자 둘이서 소주 5병 까고 2차갔는데 기억이없음. 수제비만 리필해서 두 번 정도 더 먹고 싶었음. 여기는 메인 메뉴가 ... 뼈구이랑 수육전골 + 수제비인데, 2명이서 가면 둘다 먹을 수는.. 없는 .. 그래서 무조건 4명이서 가서 뼈구이 하나 수육전골 하나 먹어야함 ㅠㅠ !! 감자탕도 진짜 진짜 맛있어 보였는데 못먹었음 .. 왜냐면 배가 없어서 .....
여기는 진짜 꼭 4명이서 가길 바람 그래야 여러 종류로 먹을 수 있음', v_at, v_at) RETURNING id INTO v_save;
  INSERT INTO save_tag (save_id, tag_id) SELECT v_save, unnest(ARRAY['tag_friend','tag_tasty']::text[]);
  INSERT INTO media_asset (owner_id, s3_key, content_type, content_length, status, created_at, attached_at)
  VALUES (903, 'seed/ut2/g4-golsu/01.jpg', 'image/jpeg', 457239, 'ATTACHED', v_at, v_at)
  ON CONFLICT (s3_key) DO NOTHING;
  SELECT id INTO v_asset FROM media_asset WHERE s3_key = 'seed/ut2/g4-golsu/01.jpg';
  INSERT INTO save_photo (save_id, media_asset_id, photo_order) VALUES (v_save, v_asset, 0);
  INSERT INTO media_asset (owner_id, s3_key, content_type, content_length, status, created_at, attached_at)
  VALUES (903, 'seed/ut2/g4-golsu/02.jpg', 'image/jpeg', 515734, 'ATTACHED', v_at, v_at)
  ON CONFLICT (s3_key) DO NOTHING;
  SELECT id INTO v_asset FROM media_asset WHERE s3_key = 'seed/ut2/g4-golsu/02.jpg';
  INSERT INTO save_photo (save_id, media_asset_id, photo_order) VALUES (v_save, v_asset, 1);
  INSERT INTO media_asset (owner_id, s3_key, content_type, content_length, status, created_at, attached_at)
  VALUES (903, 'seed/ut2/g4-golsu/03.jpg', 'image/jpeg', 387056, 'ATTACHED', v_at, v_at)
  ON CONFLICT (s3_key) DO NOTHING;
  SELECT id INTO v_asset FROM media_asset WHERE s3_key = 'seed/ut2/g4-golsu/03.jpg';
  INSERT INTO save_photo (save_id, media_asset_id, photo_order) VALUES (v_save, v_asset, 2);
  INSERT INTO review (save_id, user_id, place_id, created_at)
  VALUES (v_save, 903, v_place, v_at) RETURNING id INTO v_review;
  INSERT INTO review_ai_summary (review_id, pros, cons, model, created_at)
  VALUES (v_review, '수제비와 수육전골 조합이 훌륭해요', '2명이서는 메뉴를 다양하게 못 시켜요', 'seed-human', v_at);
  INSERT INTO reward_grant (user_id, reward_type, source_type, source_id, created_at)
  VALUES (903, 'GROUP_JOIN_TICKET', 'REVIEW', v_review, v_at) RETURNING id INTO v_grant;
  INSERT INTO group_join_ticket (user_id, reward_grant_id, status, created_at)
  VALUES (903, v_grant, 'AVAILABLE', v_at);
  INSERT INTO group_review_share (group_id, review_id, user_id, created_at)
  SELECT group_id, v_review, 903, v_at FROM _sg WHERE idx = 3;

  -- [12] hwadol
  v_at := v_reviewed + interval '24 hours';
  SELECT place_id INTO v_place FROM _sp WHERE key = 'hwadol';
  INSERT INTO save (user_id, place_id, rating, content, created_at, updated_at)
  VALUES (903, v_place, 4, '신정역 근처 내 원픽 맛집. 원래 맛집은 아저씨들 얼마나 있는지 보고 알 수 있다고 했는데 가보면 할아버지랑 아저씨밖에 없음. 나 갔을때는 할아버지들 회식하고 있었음. 오리고기집인데 삼겹살이 더 맛있음 그리고ㅠㅠ 오리고기, 냉동, 생삼겹 다 먹어봤는데 생삼겹이 제일 맛있기는 함. 근데 가게가 오래되서 깨끗한거 좋아하고 화장실, 매장 시설 중요하게 생각하는 사람은 별로일수도 있음.', v_at, v_at) RETURNING id INTO v_save;
  INSERT INTO save_tag (save_id, tag_id) SELECT v_save, unnest(ARRAY['tag_friend','tag_tasty']::text[]);
  INSERT INTO media_asset (owner_id, s3_key, content_type, content_length, status, created_at, attached_at)
  VALUES (903, 'seed/ut2/g5-hwadol/01.jpg', 'image/jpeg', 478007, 'ATTACHED', v_at, v_at)
  ON CONFLICT (s3_key) DO NOTHING;
  SELECT id INTO v_asset FROM media_asset WHERE s3_key = 'seed/ut2/g5-hwadol/01.jpg';
  INSERT INTO save_photo (save_id, media_asset_id, photo_order) VALUES (v_save, v_asset, 0);
  INSERT INTO media_asset (owner_id, s3_key, content_type, content_length, status, created_at, attached_at)
  VALUES (903, 'seed/ut2/g5-hwadol/02.jpg', 'image/jpeg', 488451, 'ATTACHED', v_at, v_at)
  ON CONFLICT (s3_key) DO NOTHING;
  SELECT id INTO v_asset FROM media_asset WHERE s3_key = 'seed/ut2/g5-hwadol/02.jpg';
  INSERT INTO save_photo (save_id, media_asset_id, photo_order) VALUES (v_save, v_asset, 1);
  INSERT INTO media_asset (owner_id, s3_key, content_type, content_length, status, created_at, attached_at)
  VALUES (903, 'seed/ut2/g5-hwadol/03.jpg', 'image/jpeg', 502558, 'ATTACHED', v_at, v_at)
  ON CONFLICT (s3_key) DO NOTHING;
  SELECT id INTO v_asset FROM media_asset WHERE s3_key = 'seed/ut2/g5-hwadol/03.jpg';
  INSERT INTO save_photo (save_id, media_asset_id, photo_order) VALUES (v_save, v_asset, 2);
  INSERT INTO review (save_id, user_id, place_id, created_at)
  VALUES (v_save, 903, v_place, v_at) RETURNING id INTO v_review;
  INSERT INTO review_ai_summary (review_id, pros, cons, model, created_at)
  VALUES (v_review, '생삼겹이 특히 맛있어요', '매장 시설이 오래됐어요', 'seed-human', v_at);
  INSERT INTO reward_grant (user_id, reward_type, source_type, source_id, created_at)
  VALUES (903, 'GROUP_JOIN_TICKET', 'REVIEW', v_review, v_at) RETURNING id INTO v_grant;
  INSERT INTO group_join_ticket (user_id, reward_grant_id, status, created_at)
  VALUES (903, v_grant, 'AVAILABLE', v_at);
  INSERT INTO group_review_share (group_id, review_id, user_id, created_at)
  SELECT group_id, v_review, 903, v_at FROM _sg WHERE idx = 4;

  -- [13] kushinoa
  v_at := v_reviewed + interval '26 hours';
  SELECT place_id INTO v_place FROM _sp WHERE key = 'kushinoa';
  INSERT INTO save (user_id, place_id, rating, content, created_at, updated_at)
  VALUES (902, v_place, 5, '가을 - 겨울 ... 단새우 & 우니
철 가시기전에 지금 가야함. 그리고 오뎅바 싫어하는사람? 여기 가야함. 여기 안가봐서 오뎅바 싫어하는 거임 ㄹㅇ', v_at, v_at) RETURNING id INTO v_save;
  INSERT INTO save_tag (save_id, tag_id) SELECT v_save, unnest(ARRAY['tag_friend','tag_tasty']::text[]);
  INSERT INTO media_asset (owner_id, s3_key, content_type, content_length, status, created_at, attached_at)
  VALUES (902, 'seed/ut2/g5-kushinoa/01.jpg', 'image/jpeg', 419419, 'ATTACHED', v_at, v_at)
  ON CONFLICT (s3_key) DO NOTHING;
  SELECT id INTO v_asset FROM media_asset WHERE s3_key = 'seed/ut2/g5-kushinoa/01.jpg';
  INSERT INTO save_photo (save_id, media_asset_id, photo_order) VALUES (v_save, v_asset, 0);
  INSERT INTO media_asset (owner_id, s3_key, content_type, content_length, status, created_at, attached_at)
  VALUES (902, 'seed/ut2/g5-kushinoa/02.jpg', 'image/jpeg', 258197, 'ATTACHED', v_at, v_at)
  ON CONFLICT (s3_key) DO NOTHING;
  SELECT id INTO v_asset FROM media_asset WHERE s3_key = 'seed/ut2/g5-kushinoa/02.jpg';
  INSERT INTO save_photo (save_id, media_asset_id, photo_order) VALUES (v_save, v_asset, 1);
  INSERT INTO media_asset (owner_id, s3_key, content_type, content_length, status, created_at, attached_at)
  VALUES (902, 'seed/ut2/g5-kushinoa/03.jpg', 'image/jpeg', 435603, 'ATTACHED', v_at, v_at)
  ON CONFLICT (s3_key) DO NOTHING;
  SELECT id INTO v_asset FROM media_asset WHERE s3_key = 'seed/ut2/g5-kushinoa/03.jpg';
  INSERT INTO save_photo (save_id, media_asset_id, photo_order) VALUES (v_save, v_asset, 2);
  INSERT INTO review (save_id, user_id, place_id, created_at)
  VALUES (v_save, 902, v_place, v_at) RETURNING id INTO v_review;
  INSERT INTO review_ai_summary (review_id, pros, cons, model, created_at)
  VALUES (v_review, '제철 단새우와 우니가 훌륭해요', '제철이 지나면 아쉬울 수 있어요', 'seed-human', v_at);
  INSERT INTO reward_grant (user_id, reward_type, source_type, source_id, created_at)
  VALUES (902, 'GROUP_JOIN_TICKET', 'REVIEW', v_review, v_at) RETURNING id INTO v_grant;
  INSERT INTO group_join_ticket (user_id, reward_grant_id, status, created_at)
  VALUES (902, v_grant, 'AVAILABLE', v_at);
  INSERT INTO group_review_share (group_id, review_id, user_id, created_at)
  SELECT group_id, v_review, 902, v_at FROM _sg WHERE idx = 4;

  -- [14] sodam
  v_at := v_reviewed + interval '28 hours';
  SELECT place_id INTO v_place FROM _sp WHERE key = 'sodam';
  INSERT INTO save (user_id, place_id, rating, content, created_at, updated_at)
  VALUES (903, v_place, 5, '평냉 덕후들 모여라. 4계절 내내 평냉 먹을 수 있는, 메밀면을 가게에서 뽑는 자가제면 평냉집 딱 알려준다. 진짜 여기 안가봤으면 평냉 먹었다고 할 수 없음. 여기 그냥 ''메밀면'' 자체가 맛있어서 국물? 양념 없이 진짜 순수 면만 먹어도 고소함..', v_at, v_at) RETURNING id INTO v_save;
  INSERT INTO save_tag (save_id, tag_id) SELECT v_save, unnest(ARRAY['tag_alone','tag_tasty']::text[]);
  INSERT INTO media_asset (owner_id, s3_key, content_type, content_length, status, created_at, attached_at)
  VALUES (903, 'seed/ut2/g5-sodam/01.jpg', 'image/jpeg', 275045, 'ATTACHED', v_at, v_at)
  ON CONFLICT (s3_key) DO NOTHING;
  SELECT id INTO v_asset FROM media_asset WHERE s3_key = 'seed/ut2/g5-sodam/01.jpg';
  INSERT INTO save_photo (save_id, media_asset_id, photo_order) VALUES (v_save, v_asset, 0);
  INSERT INTO media_asset (owner_id, s3_key, content_type, content_length, status, created_at, attached_at)
  VALUES (903, 'seed/ut2/g5-sodam/02.jpg', 'image/jpeg', 309988, 'ATTACHED', v_at, v_at)
  ON CONFLICT (s3_key) DO NOTHING;
  SELECT id INTO v_asset FROM media_asset WHERE s3_key = 'seed/ut2/g5-sodam/02.jpg';
  INSERT INTO save_photo (save_id, media_asset_id, photo_order) VALUES (v_save, v_asset, 1);
  INSERT INTO media_asset (owner_id, s3_key, content_type, content_length, status, created_at, attached_at)
  VALUES (903, 'seed/ut2/g5-sodam/03.jpg', 'image/jpeg', 448822, 'ATTACHED', v_at, v_at)
  ON CONFLICT (s3_key) DO NOTHING;
  SELECT id INTO v_asset FROM media_asset WHERE s3_key = 'seed/ut2/g5-sodam/03.jpg';
  INSERT INTO save_photo (save_id, media_asset_id, photo_order) VALUES (v_save, v_asset, 2);
  INSERT INTO review (save_id, user_id, place_id, created_at)
  VALUES (v_save, 903, v_place, v_at) RETURNING id INTO v_review;
  INSERT INTO review_ai_summary (review_id, pros, cons, model, created_at)
  VALUES (v_review, '자가제면 메밀면이 고소해요', '면 외 메뉴는 선택지가 좁아요', 'seed-human', v_at);
  INSERT INTO reward_grant (user_id, reward_type, source_type, source_id, created_at)
  VALUES (903, 'GROUP_JOIN_TICKET', 'REVIEW', v_review, v_at) RETURNING id INTO v_grant;
  INSERT INTO group_join_ticket (user_id, reward_grant_id, status, created_at)
  VALUES (903, v_grant, 'AVAILABLE', v_at);
  INSERT INTO group_review_share (group_id, review_id, user_id, created_at)
  SELECT group_id, v_review, 903, v_at FROM _sg WHERE idx = 4;

  -- [15] byeolmi
  v_at := v_reviewed + interval '30 hours';
  SELECT place_id INTO v_place FROM _sp WHERE key = 'byeolmi';
  INSERT INTO save (user_id, place_id, rating, content, created_at, updated_at)
  VALUES (902, v_place, 4, '잠실에서 제일 유명한 곱창집. 유명하면 이유가 있음. 내 최애 곱창 맛집임ㅠㅠ 근데 사람이 좀 많은 편. 곱창은 맛있긴 하지만 서비스가 좀 아쉽긴함.', v_at, v_at) RETURNING id INTO v_save;
  INSERT INTO save_tag (save_id, tag_id) SELECT v_save, unnest(ARRAY['tag_friend','tag_tasty']::text[]);
  INSERT INTO media_asset (owner_id, s3_key, content_type, content_length, status, created_at, attached_at)
  VALUES (902, 'seed/ut2/g6-byeolmi/01.jpg', 'image/jpeg', 539711, 'ATTACHED', v_at, v_at)
  ON CONFLICT (s3_key) DO NOTHING;
  SELECT id INTO v_asset FROM media_asset WHERE s3_key = 'seed/ut2/g6-byeolmi/01.jpg';
  INSERT INTO save_photo (save_id, media_asset_id, photo_order) VALUES (v_save, v_asset, 0);
  INSERT INTO media_asset (owner_id, s3_key, content_type, content_length, status, created_at, attached_at)
  VALUES (902, 'seed/ut2/g6-byeolmi/02.jpg', 'image/jpeg', 696983, 'ATTACHED', v_at, v_at)
  ON CONFLICT (s3_key) DO NOTHING;
  SELECT id INTO v_asset FROM media_asset WHERE s3_key = 'seed/ut2/g6-byeolmi/02.jpg';
  INSERT INTO save_photo (save_id, media_asset_id, photo_order) VALUES (v_save, v_asset, 1);
  INSERT INTO review (save_id, user_id, place_id, created_at)
  VALUES (v_save, 902, v_place, v_at) RETURNING id INTO v_review;
  INSERT INTO review_ai_summary (review_id, pros, cons, model, created_at)
  VALUES (v_review, '곱창 맛은 잠실 최고 수준이에요', '사람이 많고 서비스가 아쉬워요', 'seed-human', v_at);
  INSERT INTO reward_grant (user_id, reward_type, source_type, source_id, created_at)
  VALUES (902, 'GROUP_JOIN_TICKET', 'REVIEW', v_review, v_at) RETURNING id INTO v_grant;
  INSERT INTO group_join_ticket (user_id, reward_grant_id, status, created_at)
  VALUES (902, v_grant, 'AVAILABLE', v_at);
  INSERT INTO group_review_share (group_id, review_id, user_id, created_at)
  SELECT group_id, v_review, 902, v_at FROM _sg WHERE idx = 5;

  -- [16] geumdon
  v_at := v_reviewed + interval '32 hours';
  SELECT place_id INTO v_place FROM _sp WHERE key = 'geumdon';
  INSERT INTO save (user_id, place_id, rating, content, created_at, updated_at)
  VALUES (902, v_place, 4, '맛있는거 먹고싶을 때 맨날 가는 맛집. 생갈비랑 양념갈비가 맛있음. 전담 매니저가 직접 구워줘서 편하게 먹을 수 있음. 근데 가격이 좀 사악하긴 함…. 그리고 대기가 길어서 갈거면 미리 테이블링 해야 갈 수 있음.', v_at, v_at) RETURNING id INTO v_save;
  INSERT INTO save_tag (save_id, tag_id) SELECT v_save, unnest(ARRAY['tag_family','tag_tasty','tag_kind']::text[]);
  INSERT INTO media_asset (owner_id, s3_key, content_type, content_length, status, created_at, attached_at)
  VALUES (902, 'seed/ut2/g6-geumdon/01.jpg', 'image/jpeg', 540794, 'ATTACHED', v_at, v_at)
  ON CONFLICT (s3_key) DO NOTHING;
  SELECT id INTO v_asset FROM media_asset WHERE s3_key = 'seed/ut2/g6-geumdon/01.jpg';
  INSERT INTO save_photo (save_id, media_asset_id, photo_order) VALUES (v_save, v_asset, 0);
  INSERT INTO media_asset (owner_id, s3_key, content_type, content_length, status, created_at, attached_at)
  VALUES (902, 'seed/ut2/g6-geumdon/02.jpg', 'image/jpeg', 333331, 'ATTACHED', v_at, v_at)
  ON CONFLICT (s3_key) DO NOTHING;
  SELECT id INTO v_asset FROM media_asset WHERE s3_key = 'seed/ut2/g6-geumdon/02.jpg';
  INSERT INTO save_photo (save_id, media_asset_id, photo_order) VALUES (v_save, v_asset, 1);
  INSERT INTO media_asset (owner_id, s3_key, content_type, content_length, status, created_at, attached_at)
  VALUES (902, 'seed/ut2/g6-geumdon/03.jpg', 'image/jpeg', 454078, 'ATTACHED', v_at, v_at)
  ON CONFLICT (s3_key) DO NOTHING;
  SELECT id INTO v_asset FROM media_asset WHERE s3_key = 'seed/ut2/g6-geumdon/03.jpg';
  INSERT INTO save_photo (save_id, media_asset_id, photo_order) VALUES (v_save, v_asset, 2);
  INSERT INTO review (save_id, user_id, place_id, created_at)
  VALUES (v_save, 902, v_place, v_at) RETURNING id INTO v_review;
  INSERT INTO review_ai_summary (review_id, pros, cons, model, created_at)
  VALUES (v_review, '전담 매니저가 구워줘서 편해요', '가격이 높고 대기가 길어요', 'seed-human', v_at);
  INSERT INTO reward_grant (user_id, reward_type, source_type, source_id, created_at)
  VALUES (902, 'GROUP_JOIN_TICKET', 'REVIEW', v_review, v_at) RETURNING id INTO v_grant;
  INSERT INTO group_join_ticket (user_id, reward_grant_id, status, created_at)
  VALUES (902, v_grant, 'AVAILABLE', v_at);
  INSERT INTO group_review_share (group_id, review_id, user_id, created_at)
  SELECT group_id, v_review, 902, v_at FROM _sg WHERE idx = 5;

  -- [17] thebitnam
  v_at := v_reviewed + interval '34 hours';
  SELECT place_id INTO v_place FROM _sp WHERE key = 'thebitnam';
  INSERT INTO save (user_id, place_id, rating, content, created_at, updated_at)
  VALUES (901, v_place, 5, '진짜 너무 친절하셔서 또 가고싶음… 첨 갔을 때 사람들 앞에 있길래 무슨 웨이팅을 하나 싶었는데 양도 많고 너무 맛있었음… 너무 웨이팅이 심하긴 하지만… 윤남노랑 풍자가 극찬한 이유를 알겠음. 처음에 쌀국수 별로 안좋아했는데 이 매장 알고나서부터 쌀국수 찾아먹는 사람 됨. 고수도 추가할 수 있고 도가니가 들어간 쌀국수 강추! 몰랐는데 캐치테이블 예약도 가능한거 같음. 우리집 경기도인데 이거 먹으러 잠실 놀러 쌉가능', v_at, v_at) RETURNING id INTO v_save;
  INSERT INTO save_tag (save_id, tag_id) SELECT v_save, unnest(ARRAY['tag_friend','tag_kind','tag_tasty']::text[]);
  INSERT INTO media_asset (owner_id, s3_key, content_type, content_length, status, created_at, attached_at)
  VALUES (901, 'seed/ut2/g6-thebitnam/01.jpg', 'image/jpeg', 496284, 'ATTACHED', v_at, v_at)
  ON CONFLICT (s3_key) DO NOTHING;
  SELECT id INTO v_asset FROM media_asset WHERE s3_key = 'seed/ut2/g6-thebitnam/01.jpg';
  INSERT INTO save_photo (save_id, media_asset_id, photo_order) VALUES (v_save, v_asset, 0);
  INSERT INTO media_asset (owner_id, s3_key, content_type, content_length, status, created_at, attached_at)
  VALUES (901, 'seed/ut2/g6-thebitnam/02.jpg', 'image/jpeg', 378252, 'ATTACHED', v_at, v_at)
  ON CONFLICT (s3_key) DO NOTHING;
  SELECT id INTO v_asset FROM media_asset WHERE s3_key = 'seed/ut2/g6-thebitnam/02.jpg';
  INSERT INTO save_photo (save_id, media_asset_id, photo_order) VALUES (v_save, v_asset, 1);
  INSERT INTO review (save_id, user_id, place_id, created_at)
  VALUES (v_save, 901, v_place, v_at) RETURNING id INTO v_review;
  INSERT INTO review_ai_summary (review_id, pros, cons, model, created_at)
  VALUES (v_review, '도가니 쌀국수가 훌륭하고 친절해요', '웨이팅이 심한 편이에요', 'seed-human', v_at);
  INSERT INTO reward_grant (user_id, reward_type, source_type, source_id, created_at)
  VALUES (901, 'GROUP_JOIN_TICKET', 'REVIEW', v_review, v_at) RETURNING id INTO v_grant;
  INSERT INTO group_join_ticket (user_id, reward_grant_id, status, created_at)
  VALUES (901, v_grant, 'AVAILABLE', v_at);
  INSERT INTO group_review_share (group_id, review_id, user_id, created_at)
  SELECT group_id, v_review, 901, v_at FROM _sg WHERE idx = 5;

  -- [18] hikiniku
  v_at := v_reviewed + interval '36 hours';
  SELECT place_id INTO v_place FROM _sp WHERE key = 'hikiniku';
  INSERT INTO save (user_id, place_id, rating, content, created_at, updated_at)
  VALUES (902, v_place, 5, '회사 동료에게 추천을 받아 방문한 전설의 후쿠오카 함바그 맛집. 진짜 일본이 따로 없는 맛 ,,, 매일매일 다른 쌀로 ... 짓는 솥밥과 .. 첫번째 함바그입니다 ~ 하면서 주는 고기의 조화가 미친 ..집 .. 꿀팁은 꼭꼭 처음부터 웨이팅있으니 캐치테이블 예약하시고 맥주는 꼭꼭 드세요 (positive) 저 원래 맥주 못먹는데 흡입함', v_at, v_at) RETURNING id INTO v_save;
  INSERT INTO save_tag (save_id, tag_id) SELECT v_save, unnest(ARRAY['tag_colleague','tag_tasty']::text[]);
  INSERT INTO media_asset (owner_id, s3_key, content_type, content_length, status, created_at, attached_at)
  VALUES (902, 'seed/ut2/g7-hikiniku/01.jpg', 'image/jpeg', 407242, 'ATTACHED', v_at, v_at)
  ON CONFLICT (s3_key) DO NOTHING;
  SELECT id INTO v_asset FROM media_asset WHERE s3_key = 'seed/ut2/g7-hikiniku/01.jpg';
  INSERT INTO save_photo (save_id, media_asset_id, photo_order) VALUES (v_save, v_asset, 0);
  INSERT INTO media_asset (owner_id, s3_key, content_type, content_length, status, created_at, attached_at)
  VALUES (902, 'seed/ut2/g7-hikiniku/02.jpg', 'image/jpeg', 327971, 'ATTACHED', v_at, v_at)
  ON CONFLICT (s3_key) DO NOTHING;
  SELECT id INTO v_asset FROM media_asset WHERE s3_key = 'seed/ut2/g7-hikiniku/02.jpg';
  INSERT INTO save_photo (save_id, media_asset_id, photo_order) VALUES (v_save, v_asset, 1);
  INSERT INTO media_asset (owner_id, s3_key, content_type, content_length, status, created_at, attached_at)
  VALUES (902, 'seed/ut2/g7-hikiniku/03.jpg', 'image/jpeg', 416385, 'ATTACHED', v_at, v_at)
  ON CONFLICT (s3_key) DO NOTHING;
  SELECT id INTO v_asset FROM media_asset WHERE s3_key = 'seed/ut2/g7-hikiniku/03.jpg';
  INSERT INTO save_photo (save_id, media_asset_id, photo_order) VALUES (v_save, v_asset, 2);
  INSERT INTO review (save_id, user_id, place_id, created_at)
  VALUES (v_save, 902, v_place, v_at) RETURNING id INTO v_review;
  INSERT INTO review_ai_summary (review_id, pros, cons, model, created_at)
  VALUES (v_review, '솥밥과 함바그 조합이 훌륭해요', '웨이팅이 있어 예약이 필요해요', 'seed-human', v_at);
  INSERT INTO reward_grant (user_id, reward_type, source_type, source_id, created_at)
  VALUES (902, 'GROUP_JOIN_TICKET', 'REVIEW', v_review, v_at) RETURNING id INTO v_grant;
  INSERT INTO group_join_ticket (user_id, reward_grant_id, status, created_at)
  VALUES (902, v_grant, 'AVAILABLE', v_at);
  INSERT INTO group_review_share (group_id, review_id, user_id, created_at)
  SELECT group_id, v_review, 902, v_at FROM _sg WHERE idx = 6;

  -- [19] mur
  v_at := v_reviewed + interval '38 hours';
  SELECT place_id INTO v_place FROM _sp WHERE key = 'mur';
  INSERT INTO save (user_id, place_id, rating, content, created_at, updated_at)
  VALUES (903, v_place, 4, '역삼역 근처에서 분위기 좋고 가성비 좋은 이자카야를 찾는다면 추천드려요:) 안주 종류가 다양하고 가격도 합리적이라 역삼에서 약속이 있을 때마다 자주 방문하는 곳이에요. 다만 안주가 저렴한 대신 소주는 판매하지 않고 매장이 크지 않아 인기 많은 시간대에는 자리가 없을 수도 있으니 참고하세요!', v_at, v_at) RETURNING id INTO v_save;
  INSERT INTO save_tag (save_id, tag_id) SELECT v_save, unnest(ARRAY['tag_friend','tag_value','tag_mood']::text[]);
  INSERT INTO media_asset (owner_id, s3_key, content_type, content_length, status, created_at, attached_at)
  VALUES (903, 'seed/ut2/g7-mur/01.jpg', 'image/jpeg', 441676, 'ATTACHED', v_at, v_at)
  ON CONFLICT (s3_key) DO NOTHING;
  SELECT id INTO v_asset FROM media_asset WHERE s3_key = 'seed/ut2/g7-mur/01.jpg';
  INSERT INTO save_photo (save_id, media_asset_id, photo_order) VALUES (v_save, v_asset, 0);
  INSERT INTO media_asset (owner_id, s3_key, content_type, content_length, status, created_at, attached_at)
  VALUES (903, 'seed/ut2/g7-mur/02.jpg', 'image/jpeg', 431990, 'ATTACHED', v_at, v_at)
  ON CONFLICT (s3_key) DO NOTHING;
  SELECT id INTO v_asset FROM media_asset WHERE s3_key = 'seed/ut2/g7-mur/02.jpg';
  INSERT INTO save_photo (save_id, media_asset_id, photo_order) VALUES (v_save, v_asset, 1);
  INSERT INTO media_asset (owner_id, s3_key, content_type, content_length, status, created_at, attached_at)
  VALUES (903, 'seed/ut2/g7-mur/03.jpg', 'image/jpeg', 339364, 'ATTACHED', v_at, v_at)
  ON CONFLICT (s3_key) DO NOTHING;
  SELECT id INTO v_asset FROM media_asset WHERE s3_key = 'seed/ut2/g7-mur/03.jpg';
  INSERT INTO save_photo (save_id, media_asset_id, photo_order) VALUES (v_save, v_asset, 2);
  INSERT INTO review (save_id, user_id, place_id, created_at)
  VALUES (v_save, 903, v_place, v_at) RETURNING id INTO v_review;
  INSERT INTO review_ai_summary (review_id, pros, cons, model, created_at)
  VALUES (v_review, '안주가 다양하고 가격이 합리적이에요', '소주가 없고 매장이 작아요', 'seed-human', v_at);
  INSERT INTO reward_grant (user_id, reward_type, source_type, source_id, created_at)
  VALUES (903, 'GROUP_JOIN_TICKET', 'REVIEW', v_review, v_at) RETURNING id INTO v_grant;
  INSERT INTO group_join_ticket (user_id, reward_grant_id, status, created_at)
  VALUES (903, v_grant, 'AVAILABLE', v_at);
  INSERT INTO group_review_share (group_id, review_id, user_id, created_at)
  SELECT group_id, v_review, 903, v_at FROM _sg WHERE idx = 6;

  -- [20] younghyang
  v_at := v_reviewed + interval '40 hours';
  SELECT place_id INTO v_place FROM _sp WHERE key = 'younghyang';
  INSERT INTO save (user_id, place_id, rating, content, created_at, updated_at)
  VALUES (901, v_place, 4, '점심때 회사 사람들이랑 파스타 먹고싶으면 가끔 가는곳. 구디에는 파스타집이 몇개 없어서 너무 귀한 곳이에요. 제발 없어지지마… 금액은 한 12000원대에서 15000원 사이였던 것 같고 피자같은 메뉴가 없긴함. 그래도 명량크림파스타랑, 명란오일파스타? 존맛! 근데 메뉴가 좀 늦게 나오는것 같음(내 체감인가…?) 그리고 메뉴 그릇이 큰데 테이블이 작아서 여러개 메뉴 시키면 좀 불편하긴 해요ㅠㅠ 그리고 거리도 좀 있음. 그치만 점심에 가면 아이스아메리카노 공짜로 먹을 수 있어요ㅎㅎ', v_at, v_at) RETURNING id INTO v_save;
  INSERT INTO save_tag (save_id, tag_id) SELECT v_save, unnest(ARRAY['tag_colleague','tag_tasty']::text[]);
  INSERT INTO media_asset (owner_id, s3_key, content_type, content_length, status, created_at, attached_at)
  VALUES (901, 'seed/ut2/g3-younghyang/01.jpg', 'image/jpeg', 482916, 'ATTACHED', v_at, v_at)
  ON CONFLICT (s3_key) DO NOTHING;
  SELECT id INTO v_asset FROM media_asset WHERE s3_key = 'seed/ut2/g3-younghyang/01.jpg';
  INSERT INTO save_photo (save_id, media_asset_id, photo_order) VALUES (v_save, v_asset, 0);
  INSERT INTO review (save_id, user_id, place_id, created_at)
  VALUES (v_save, 901, v_place, v_at) RETURNING id INTO v_review;
  INSERT INTO review_ai_summary (review_id, pros, cons, model, created_at)
  VALUES (v_review, '구디에 몇 없는 파스타집이고 점심엔 커피가 무료예요', '메뉴가 늦게 나오고 테이블이 좁아요', 'seed-human', v_at);
  INSERT INTO reward_grant (user_id, reward_type, source_type, source_id, created_at)
  VALUES (901, 'GROUP_JOIN_TICKET', 'REVIEW', v_review, v_at) RETURNING id INTO v_grant;
  INSERT INTO group_join_ticket (user_id, reward_grant_id, status, created_at)
  VALUES (901, v_grant, 'AVAILABLE', v_at);
  INSERT INTO group_review_share (group_id, review_id, user_id, created_at)
  SELECT group_id, v_review, 901, v_at FROM _sg WHERE idx = 2;

  -- [21] sotnaeum
  v_at := v_reviewed + interval '42 hours';
  SELECT place_id INTO v_place FROM _sp WHERE key = 'sotnaeum';
  INSERT INTO save (user_id, place_id, rating, content, created_at, updated_at)
  VALUES (901, v_place, 5, '지금은 이직했는데 이직하기 전 마지막 점심으로 갔던 곳… 이제 못가게 되서 너무 아쉬워요. 이직해서 제일 아쉬운건 여기 가끔 생각나는데 못가는것 하나… 솥밥 메뉴도 종류별로 있고 마지막에 누룽지까지 만들어 먹을 수 있어서 좋아요! 스테이크 솥밥, 문어 솥밥 추천합니당. 평소에 웨이팅이 살짝 있는 편이라서 방문할거면 점심때 살짝 일찍 나오세요! 매장도 협소해서 4명이상 방문하면 불편할 수도 있음.', v_at, v_at) RETURNING id INTO v_save;
  INSERT INTO save_tag (save_id, tag_id) SELECT v_save, unnest(ARRAY['tag_colleague','tag_tasty']::text[]);
  INSERT INTO media_asset (owner_id, s3_key, content_type, content_length, status, created_at, attached_at)
  VALUES (901, 'seed/ut2/g8-sotnaeum/01.jpg', 'image/jpeg', 454040, 'ATTACHED', v_at, v_at)
  ON CONFLICT (s3_key) DO NOTHING;
  SELECT id INTO v_asset FROM media_asset WHERE s3_key = 'seed/ut2/g8-sotnaeum/01.jpg';
  INSERT INTO save_photo (save_id, media_asset_id, photo_order) VALUES (v_save, v_asset, 0);
  INSERT INTO media_asset (owner_id, s3_key, content_type, content_length, status, created_at, attached_at)
  VALUES (901, 'seed/ut2/g8-sotnaeum/02.jpg', 'image/jpeg', 488116, 'ATTACHED', v_at, v_at)
  ON CONFLICT (s3_key) DO NOTHING;
  SELECT id INTO v_asset FROM media_asset WHERE s3_key = 'seed/ut2/g8-sotnaeum/02.jpg';
  INSERT INTO save_photo (save_id, media_asset_id, photo_order) VALUES (v_save, v_asset, 1);
  INSERT INTO review (save_id, user_id, place_id, created_at)
  VALUES (v_save, 901, v_place, v_at) RETURNING id INTO v_review;
  INSERT INTO review_ai_summary (review_id, pros, cons, model, created_at)
  VALUES (v_review, '솥밥 종류가 다양하고 누룽지까지 즐길 수 있어요', '웨이팅이 있고 매장이 협소해요', 'seed-human', v_at);
  INSERT INTO reward_grant (user_id, reward_type, source_type, source_id, created_at)
  VALUES (901, 'GROUP_JOIN_TICKET', 'REVIEW', v_review, v_at) RETURNING id INTO v_grant;
  INSERT INTO group_join_ticket (user_id, reward_grant_id, status, created_at)
  VALUES (901, v_grant, 'AVAILABLE', v_at);
  INSERT INTO group_review_share (group_id, review_id, user_id, created_at)
  SELECT group_id, v_review, 901, v_at FROM _sg WHERE idx = 7;

  -- [22] menshokatsu
  v_at := v_reviewed + interval '44 hours';
  SELECT place_id INTO v_place FROM _sp WHERE key = 'menshokatsu';
  INSERT INTO save (user_id, place_id, rating, content, created_at, updated_at)
  VALUES (903, v_place, 5, '대존맛;; 동네 주민이라 여기 근처에 안 먹어본 가게가 없는데 오늘 첨 밥먹고 너무 당황했어요. 너무 맛있어서ㅋㅋㅋ! 먹는 법도 친절하게 설명해주셔서 좋았고 돈카츠 염지도 호감… 파김치도 호감… 돈카츠랑 우동 각각 어울리는 국 따로 나온 디테일 미쳤음… 등심카츠는 비계 부위가 다소 있는 편이라, 담백한 걸 선호하면 안심카츠 쪽이 더 나을 수 있음. 점심시간때 방문하면 약간 늦게 나올수도 있어요!', v_at, v_at) RETURNING id INTO v_save;
  INSERT INTO save_tag (save_id, tag_id) SELECT v_save, unnest(ARRAY['tag_alone','tag_tasty','tag_kind']::text[]);
  INSERT INTO media_asset (owner_id, s3_key, content_type, content_length, status, created_at, attached_at)
  VALUES (903, 'seed/ut2/g8-menshokatsu/01.jpg', 'image/jpeg', 479348, 'ATTACHED', v_at, v_at)
  ON CONFLICT (s3_key) DO NOTHING;
  SELECT id INTO v_asset FROM media_asset WHERE s3_key = 'seed/ut2/g8-menshokatsu/01.jpg';
  INSERT INTO save_photo (save_id, media_asset_id, photo_order) VALUES (v_save, v_asset, 0);
  INSERT INTO media_asset (owner_id, s3_key, content_type, content_length, status, created_at, attached_at)
  VALUES (903, 'seed/ut2/g8-menshokatsu/02.jpg', 'image/jpeg', 454473, 'ATTACHED', v_at, v_at)
  ON CONFLICT (s3_key) DO NOTHING;
  SELECT id INTO v_asset FROM media_asset WHERE s3_key = 'seed/ut2/g8-menshokatsu/02.jpg';
  INSERT INTO save_photo (save_id, media_asset_id, photo_order) VALUES (v_save, v_asset, 1);
  INSERT INTO review (save_id, user_id, place_id, created_at)
  VALUES (v_save, 903, v_place, v_at) RETURNING id INTO v_review;
  INSERT INTO review_ai_summary (review_id, pros, cons, model, created_at)
  VALUES (v_review, '돈카츠 염지와 곁들임 구성이 훌륭하고 응대가 친절해요', '점심시간에는 음식이 늦게 나올 수 있어요', 'seed-human', v_at);
  INSERT INTO reward_grant (user_id, reward_type, source_type, source_id, created_at)
  VALUES (903, 'GROUP_JOIN_TICKET', 'REVIEW', v_review, v_at) RETURNING id INTO v_grant;
  INSERT INTO group_join_ticket (user_id, reward_grant_id, status, created_at)
  VALUES (903, v_grant, 'AVAILABLE', v_at);
  INSERT INTO group_review_share (group_id, review_id, user_id, created_at)
  SELECT group_id, v_review, 903, v_at FROM _sg WHERE idx = 7;

  -- [23] sokcho
  v_at := v_reviewed + interval '46 hours';
  SELECT place_id INTO v_place FROM _sp WHERE key = 'sokcho';
  INSERT INTO save (user_id, place_id, rating, content, created_at, updated_at)
  VALUES (901, v_place, 4, '가족이 먹어보고 맛집이라 추천해주었고, 점심특선 시래기고등어조림, 삼치구이정식 시켰고 잘먹는 성인 2명인데 양이 아주 넉넉해서 배 터지는줄요! 근데 매장 외관이 좀 낡고 오래되서 좀 더려움. 주방 위생도 좋아보이지 않아서 위생 중요하게 생각하는 사람한테는 비추. 가끔 집에서 생선 먹기 부담스러우면 방문하는거 나쁘지 않음!', v_at, v_at) RETURNING id INTO v_save;
  INSERT INTO save_tag (save_id, tag_id) SELECT v_save, unnest(ARRAY['tag_family','tag_value']::text[]);
  INSERT INTO media_asset (owner_id, s3_key, content_type, content_length, status, created_at, attached_at)
  VALUES (901, 'seed/ut2/g8-sokcho/01.jpg', 'image/jpeg', 514869, 'ATTACHED', v_at, v_at)
  ON CONFLICT (s3_key) DO NOTHING;
  SELECT id INTO v_asset FROM media_asset WHERE s3_key = 'seed/ut2/g8-sokcho/01.jpg';
  INSERT INTO save_photo (save_id, media_asset_id, photo_order) VALUES (v_save, v_asset, 0);
  INSERT INTO review (save_id, user_id, place_id, created_at)
  VALUES (v_save, 901, v_place, v_at) RETURNING id INTO v_review;
  INSERT INTO review_ai_summary (review_id, pros, cons, model, created_at)
  VALUES (v_review, '점심특선 양이 넉넉해요', '매장이 낡고 위생이 아쉬워요', 'seed-human', v_at);
  INSERT INTO reward_grant (user_id, reward_type, source_type, source_id, created_at)
  VALUES (901, 'GROUP_JOIN_TICKET', 'REVIEW', v_review, v_at) RETURNING id INTO v_grant;
  INSERT INTO group_join_ticket (user_id, reward_grant_id, status, created_at)
  VALUES (901, v_grant, 'AVAILABLE', v_at);
  INSERT INTO group_review_share (group_id, review_id, user_id, created_at)
  SELECT group_id, v_review, 901, v_at FROM _sg WHERE idx = 7;

  -- ── 파생 집계: 실제 행 수에서 재계산 (P9 · D3) ──────────────────
  UPDATE place p SET review_count = c.cnt, rating_sum = c.rsum
  FROM (
    SELECT r.place_id, count(*) AS cnt, COALESCE(sum(s.rating), 0) AS rsum
    FROM review r JOIN save s ON s.id = r.save_id
    WHERE r.deleted_at IS NULL AND r.place_id IN (SELECT place_id FROM _sp)
    GROUP BY r.place_id
  ) c
  WHERE p.id = c.place_id;

  INSERT INTO group_place (group_id, place_id, shared_review_count)
  SELECT grs.group_id, r.place_id, count(*)
  FROM group_review_share grs
  JOIN review r ON r.id = grs.review_id AND r.deleted_at IS NULL
  WHERE grs.group_id IN (SELECT group_id FROM _sg)
  GROUP BY grs.group_id, r.place_id;

  UPDATE groups g SET
    member_count = (SELECT count(*) FROM group_membership m WHERE m.group_id = g.id AND m.status = 'ACTIVE'),
    review_count = (SELECT count(*) FROM group_review_share s JOIN review r ON r.id = s.review_id AND r.deleted_at IS NULL WHERE s.group_id = g.id),
    place_count  = (SELECT count(*) FROM group_place gp WHERE gp.group_id = g.id)
  WHERE g.id IN (SELECT group_id FROM _sg);
END $seed$;

