# place 적재 파이프라인 (TMT-161)

소상공인 상가(상권)정보 서울분을 `place` 테이블에 적재한다.

## 정본 결정 — 왜 상가정보 단독인가

**TMT-160 결정 (2026-08-22, be문의 공지 후 무이견 확정).** 원본 후보는 인허가·상가정보
2종이었고 실측 결과는 [[분석] 매장 원본 데이터 실측](https://ttalkkak.atlassian.net/wiki/spaces/ttalkkak/pages/57901087)에 있다. 요약:

- 상가정보: 서울 음식 141,126건, **결측·좌표 이상치 0%, 좌표 이미 WGS84, 영업중만 수록** → 바로 적재 가능
- 인허가: 폐업 77% 필터 필요, EPSG:5174 변환 필요, 두 명단은 6할이 같은 가게라 **그냥 합치면 최소 8.7만 곳이 중복**
- 병합으로 얻는 것은 커버리지 +5%와 전화번호뿐인데, 전화번호는 원본부터 65~76% 결측
- 따라서 상가정보 단독 적재, **인허가 병합은 UT2 이후 별도 티켓**

이 결정으로 원 티켓의 좌표 변환(P4)·폐점 필터(P5)는 범위에서 빠졌다. bbox 검증은
파이프라인에 남긴다 — 좌표계가 엉뚱해도 bbox는 통과하는 함정이 실측에서 확인됐기 때문이다(§2).

## 구성

```
download.sh   data.go.kr에서 분기 zip 다운로드 (atchFileId 자동 조회)
transform.py  CSV → TSV. 음식·서울 필터, region_name 생성, 길이·bbox·중복 검증, 통계 출력
load.sh       staging COPY + upsert. 재실행 가능 (ON CONFLICT DO UPDATE)
sql/upsert.sql
```

`category_id`는 **NULL로 적재한다** — 매핑(소분류 43종 → 14종)은 TMT-162다.
`phone_number`도 NULL이다 — 상가정보에 컬럼 자체가 없다 (도메인 P10과의 괴리는 실측 §8 참고).
재적재 시 `review_count`·`rating_sum`·`category_id`는 덮지 않는다 — 서비스 데이터와 TMT-162
결과를 원본 갱신이 지우면 안 된다.

## 사용법

```bash
# 1. 다운로드 + 압축 해제 (파일명이 CP437이라 -O cp949 필요, 스크립트가 처리)
./download.sh /tmp/sangga-work

# 2. 변환 — 통계가 stderr로 나온다. kept가 실측 기준(141,126 ± 분기 변동)과 크게 다르면 멈추고 원인 확인
python3 transform.py /tmp/sangga-work/sangga/소상공인시장진흥공단_상가(상권)정보_서울_*.csv > /tmp/sangga-work/clean.tsv

# 3. 적재 — PSQL로 대상 DB를 지정
PSQL="podman exec -i tmt-postgres psql -U tmt -d tmt" ./load.sh /tmp/sangga-work/clean.tsv

# 시험 적재 (마포구만)
python3 transform.py --sigungu 마포구 ... > mapo.tsv
```

### 운영 적재 (TMT-163)

SSM Run Command 페이로드 한도(8KB) 때문에 TSV를 직접 실을 수 없다. **S3 `data/` 프리픽스를 경유한다** —
DB 인스턴스 역할에 `s3:GetObject`가 `data/*`에만 열려 있다 (`infra/terraform/backup.tf`의 `data_read`.
백업 `pg/*`는 읽을 수 없어 덤프 유출 경로가 되지 않는다).

```bash
# 1) 로컬에서 업로드 (tmt-admin 자격)
gzip -k clean.tsv
aws s3 cp clean.tsv.gz s3://ttalkkak-tmt-db-backup/data/place/seoul-<기준분기>.tsv.gz

# 2) DB 인스턴스에서 (SSM Run Command): aws s3 cp → gunzip →
#    docker exec -i postgres psql ... (load.sh와 같은 staging→COPY→upsert 단계)
```

place 테이블은 Flyway V1이 적용된 2026-08-22부터 존재한다.

## 검증 (승인 기준 대응)

- **재실행 멱등**: 같은 TSV로 `load.sh` 두 번 → `place_semas_rows` 동일 (upsert.sql 말미에 카운트 출력)
- **좌표 표본**: 적재 후 알려진 매장 좌표를 ST_DWithin으로 대조 (아래 예시)
- **건수**: transform 통계의 `kept` ↔ upsert의 `staging_rows` ↔ `place_semas_rows` 3자 일치

```sql
-- 예: 마포구 시험 적재 후, 홍대입구역 반경 300m에 매장이 잡히는지
SELECT count(*) FROM place
WHERE ST_DWithin(location, ST_SetSRID(ST_MakePoint(126.9240, 37.5568), 4326)::geography, 300);
```
