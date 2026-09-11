-- 매장 검색의 띄어쓰기 관용 — 공백을 지운 이름의 trigram 인덱스 (TMT-413)
--
-- `ILIKE '%검색어%'`는 글자가 그대로 이어져야 걸려서 띄어쓰기 하나로 0건이 된다. 운영 데이터에서
-- `위드유 용산`이 `위드유용산카페`를, `오한수 우육면`이 `오한수우육면가`를 못 찾았다. 술어에
-- `replace(name, ' ', '') ILIKE ...`를 더했고, 그 표현식이 인덱스를 탈 수 있게 여기서 만든다.
--
-- 기존 place_name_trgm(원문 이름)은 그대로 둔다 — 부분 일치와 pg_trgm 유사도(`%`) 술어가 그걸 쓴다.
-- replace()는 IMMUTABLE이라 표현식 인덱스에 쓸 수 있다.
CREATE INDEX place_name_nospace_trgm ON place USING GIN (replace(name, ' ', '') gin_trgm_ops);
