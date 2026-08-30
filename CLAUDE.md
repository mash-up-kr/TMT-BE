# TMT-BE

## Project

또맛또(TMT) 백엔드 — 그룹 안에서 맛집 평가를 공유하는 모바일 웹앱의 API 서버.

Kotlin 2.3 / Spring Boot 4.1 / JDK 21 / Gradle 9.5(멀티모듈·헥사고날) / PostgreSQL 16 + PostGIS 3.4

모듈 구조와 의존성 방향, 실행 환경은 [README.md](README.md)에 있다. 여기서 다시 쓰지 않는다.

## Canonical commands

- 빌드 + 테스트 + ktlint: `./gradlew build`
- 테스트만: `./gradlew test` / 특정 클래스: `./gradlew test --tests "SaveControllerTest"`
- 포매팅: `./gradlew ktlintFormat` (커밋 전 실행 — CI가 `ktlintCheck`로 막는다)
- 로컬 실행: `docker compose -f docker/docker-compose.local.yml up -d` 후 `./gradlew :tmt-bootstrap:bootRun`

context-path는 `/api`다. Swagger UI `http://localhost:8080/api/api-docs`, OpenAPI 문서 `http://localhost:8080/api/v3/api-docs`.

## 문서 동반 갱신 규칙

**이 레포에서 코드와 문서는 같은 PR에서 함께 움직인다.** 아래 두 가지는 코드만 바꾸고 문서를 빼먹으면
mock·FE 코드젠·실구현이 조용히 어긋나기 때문에, 작업 전에 확인하고 작업 후에 반영한다. (TMT-158)

### 1. DB 스키마를 바꾸면 — 마이그레이션이 정본이다

`tmt-output-persistence/postgres/src/main/resources/db/migration/`의 Flyway 마이그레이션이 스키마의 **정본**이고
(TMT-96), `docs/DB-SCHEMA.md`가 ERD와 설계 결정(D1~D6)의 근거다. 테이블·컬럼·인덱스·제약을 추가하거나
바꿨다면 **같은 PR에서** 둘 다 갱신한다.

- **이미 적용된 마이그레이션은 고치지 않는다.** 체크섬이 달라져 기동이 막힌다. 변경은 항상 새 `V{n}__*.sql`을
  추가하고, 스키마 변경과 참조 데이터(시드)는 파일을 나눈다
- 새 컬럼에는 어떤 도메인 규칙에서 온 것인지 주석으로 규칙 번호를 남긴다 (예: `-- P4`)
- `docs/DB-SCHEMA.md` — ERD(mermaid)와 영향받은 설계 결정 갱신. 기존 결정을 뒤집었다면 **왜 바뀌었는지**를 함께 적는다
- 실행 검증 필수 — 빈 볼륨에서 실제로 기동해보고 통과한 것만 올린다:

  ```bash
  docker compose -f docker/docker-compose.local.yml down -v
  docker compose -f docker/docker-compose.local.yml up -d
  ./gradlew :tmt-bootstrap:bootRun
  ```

엔티티(JPA) 코드만 고치고 마이그레이션을 안 만들면 로컬·운영 모두 `ddl-auto: validate`에서 기동이 막힌다.
스키마 변경은 코드보다 마이그레이션이 먼저다.

### 2. API 계약을 바꾸면 — 변경 이력이 먼저다

FE는 서버의 `/api/v3/api-docs`에서 **orval로 TypeScript 타입과 훅을 생성**한다. 응답 필드 이름이 그대로
FE 코드에 박히므로, 필드 하나를 바꾸면 FE 코드가 깨진다. 반대로 **바뀐 걸 모르고 지나가면 런타임에서야 발견된다.**

그래서 순서가 정해져 있다. 구현이 마지막이다.

1. [**[계약] API 변경 이력**](https://ttalkkak.atlassian.net/wiki/spaces/ttalkkak/pages/57769994) 문서의 변경 이력에 한 줄 추가 — 영향도를 반드시 분류한다
   - `Breaking` — FE 코드 재생성·수정 필요 (필드명·타입 변경, 필드 제거, 상태 코드 변경)
   - `Additive` — 추가만, 기존 코드 안 깨짐 (nullable 필드 추가, 새 엔드포인트)
   - `Clarify` — 계약은 그대로, 문구·근거만 정리
2. 해당 화면의 API 명세 문서 본문 수정 (아래 §규칙 출처)
3. 서버 구현·mock 반영
4. `Breaking`이면 FE에 공유 → FE가 `pnpm api:sync`로 코드 재생성

**"명세에 없어서 내가 정했다"도 기록 대상이다.** 구현하다 값을 정하면(상한, 필수 여부, 정렬 기준)
mock은 이미 그렇게 동작하는데 문서에는 없는 상태가 된다 — 변경 이력 문서의 "명세 반영이 필요한 것"에 적는다.

`ErrorCode` enum 이름은 그대로 응답의 `code` 값이고 클라이언트 분기의 기준이다. **이름 변경은 파괴적 변경이므로
고치지 말고 새 코드를 추가한다.**

## 규칙 출처 — 정본이 어디에 있나

코드에서 판단이 필요할 때 아래를 먼저 읽는다. 이 문서는 목차이지 정본이 아니다.

| 알고 싶은 것 | 정본 |
|---|---|
| 브랜치·커밋·PR·리뷰 등급(`[must]`/`[want]`/`[q]`)·머지 조건 | [docs/BRANCHING.md](docs/BRANCHING.md) |
| 릴리즈 태그·배포 절차·롤백 | [docs/RELEASE.md](docs/RELEASE.md) |
| 테이블 구조·ERD·설계 결정 | [`V1__init.sql`](tmt-output-persistence/postgres/src/main/resources/db/migration/V1__init.sql) (DDL 정본) · [docs/DB-SCHEMA.md](docs/DB-SCHEMA.md) (근거) |
| 응답 래퍼·에러 형식·커서 페이징·인증·멱등성 | [공통 API 규약 v1](https://ttalkkak.atlassian.net/wiki/spaces/ttalkkak/pages/51249170) |
| 도메인 규칙 번호(C4·G17·T6 …)의 출처 | [도메인 설계 v2](https://ttalkkak.atlassian.net/wiki/spaces/ttalkkak/pages/57049090) |
| 화면별 엔드포인트 계약 | API 명세 v2 — [A.홈](https://ttalkkak.atlassian.net/wiki/spaces/ttalkkak/pages/57049129) · [B.탐색·가게](https://ttalkkak.atlassian.net/wiki/spaces/ttalkkak/pages/57016328) · [D_01.그룹 탐색](https://ttalkkak.atlassian.net/wiki/spaces/ttalkkak/pages/56983660) · [D_02.그룹 생성·상세](https://ttalkkak.atlassian.net/wiki/spaces/ttalkkak/pages/56983617) · [F·G·I.리뷰](https://ttalkkak.atlassian.net/wiki/spaces/ttalkkak/pages/56983556) · [H.가입·공유](https://ttalkkak.atlassian.net/wiki/spaces/ttalkkak/pages/56950805) |
| 계약이 언제 왜 바뀌었나 | [[계약] API 변경 이력](https://ttalkkak.atlassian.net/wiki/spaces/ttalkkak/pages/57769994) |

주석에 `(C4)`, `(G17)` 같은 규칙 번호가 붙어 있으면 도메인 설계 v2의 그 항목이 근거다. **판단을 바꿀 때는
규칙 번호를 먼저 확인하고, 규칙 자체가 틀렸다면 코드가 아니라 문서부터 고친다.**

## Universal working agreement

- 코드를 바꾸면 `./gradlew build`를 돌린다. 일부만 돌렸다면 범위와 생략 이유를 밝힌다. 안 돌렸으면 통과했다고 말하지 않는다
- 정확한 값과 현재 동작은 코드·설정·마이그레이션을 source of truth로 본다. 문서와 코드가 다르면 추측하지 말고 **차이를 명시한다**
- 내 변경으로 문서의 서술이 코드와 달라지면 같은 변경에서 문서를 갱신한다 (위 §문서 동반 갱신 규칙)
- production 의존성을 추가하기 전에 필요성과 대안을 확인하고 보고한다
- 기존 모듈 경계(`input/output 어댑터 → application`)를 이유 없이 우회하지 않는다
- 시크릿은 커밋하지 않는다. 실수로 커밋했다면 히스토리 정리로 끝내지 말고 **즉시 rotate** (BRANCHING §7)

## Repository facts

- **mock 코드는 한시적이다.** `tmt-input-http/src/main/kotlin/com/tmt/input/http/mock/`의 컨트롤러·store는
  FE 병렬 개발을 위한 인메모리 구현이고(TMT-149), 실구현이 리소스별로 들어오면 **해당 컨트롤러와 빈만 지운다.**
  mock 코드가 `application`·`persistence` 모듈로 새지 않게 전부 `mock` 패키지 안에 가둔다
- 인증은 카카오 로그인 전까지 `X-User-Id` 헤더 스텁이다. `@UserId Long`은 필수(없으면 401), `@UserId Long?`은 선택
- **배포는 릴리즈 태그(vX.Y.Z) 발행 기준이다 — main 머지는 배포하지 않는다.** mock 기간 한정으로 켜 뒀던
  main 자동 배포(TMT-155)는 UT2 뒤 되돌렸다. mock 서버: `https://3-39-38-23.sslip.io/api` (TMT-175,
  Caddy TLS 종단 — `docker/Caddyfile`)
- Jira는 `TMT` 프로젝트(ttalkkak.atlassian.net). 브랜치·PR 제목에 티켓 키가 들어간다
- 이 문서는 Claude Code가 읽고, `AGENTS.md`는 여기로 향하는 심볼릭 링크다 — 다른 에이전트도 같은 규칙을 본다
