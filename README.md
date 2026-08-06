# TMT-BE
또맛또(TMT) 백엔드 — Spring Boot / Kotlin

## Tech Stack

| 분류 | 기술 |
|------|------|
| Language | Kotlin 2.3, JDK 21 |
| Framework | Spring Boot 4.1 |
| ORM / Query | JPA, QueryDSL |
| Database | PostgreSQL 17 (PostGIS) |
| Build | Gradle 9.5 (buildSrc convention plugin + version catalog), ktlint |
| Infra | Docker, GitHub Actions (CI/CD) |

---

## 모듈 구조 (헥사고날 아키텍처)

```
tmt-bootstrap                 ← Spring Boot 진입점, DI 조립 (모든 모듈 참조)
│
├── tmt-input-http            ← REST API Controller, 예외 응답, Swagger
│
├── tmt-application           ← 핵심 비즈니스 로직 (Port & Service)
│   ├── domain/               ← 도메인 서비스
│   ├── port/input/           ← UseCase 인터페이스
│   └── port/output/          ← Output Port 인터페이스
│
├── tmt-output-persistence    ← PostgreSQL JPA + QueryDSL 어댑터
└── tmt-common                ← 공통 예외 (TmtException, ExceptionCode)
```

- 의존성 방향: `input/output 어댑터 → application`. 어댑터는 `application`의 Port 인터페이스에만 의존한다.
- `bootstrap`은 모든 모듈을 조립만 하고 로직을 갖지 않는다.
- 빌드 공통 설정은 `buildSrc`의 `tmt-convention` 플러그인, 의존성 버전은 `gradle/libs.versions.toml`에서 관리한다.

## Getting Started

```bash
# 1. 로컬 PostgreSQL 기동
docker compose -f docker/docker-compose.local.yml up -d

# 2. 애플리케이션 실행 (기본 프로필: local)
./gradlew :tmt-bootstrap:bootRun
```

| 확인 | URL |
|------|-----|
| API 헬스 체크 | http://localhost:8080/api/health/api |
| DB 헬스 체크 | http://localhost:8080/api/health/db |
| Actuator | http://localhost:8080/api/actuator/health |
| Swagger UI | http://localhost:8080/api/api-docs |

```bash
# 빌드 + 테스트 + ktlint
./gradlew build
```

## 실행 환경

프로필은 **`local`(개인 작업 환경)과 `prod`(운영·배포)** 두 가지다.

| | `local` | `prod` |
|---|---|---|
| 설정 파일 | `application-local.yml` | `application-prod.yml` |
| DB 기동 | `docker/docker-compose.local.yml` | `docker/docker-compose.prod-postgres.yml` |
| 앱 기동 | `./gradlew :tmt-bootstrap:bootRun` | `docker/docker-compose.prod.yml` |
| DB 접속 | `localhost:5432` | `tmt-postgres:5432` (`tmt-network`) |
| `ddl-auto` | `update` | `validate` |
| DB 비밀번호 | 기본값 `12345678` | `.env`의 `POSTGRES_PASSWORD` (미설정 시 기동 실패) |

프로필을 지정하지 않으면 `local`로 뜬다. 다른 프로필로 실행하려면 `SPRING_PROFILES_ACTIVE` 또는 `--spring.profiles.active`를 쓴다.

로컬 DB 이미지는 `imresamu/postgis`다. 공식 `postgis/postgis`가 arm64 이미지를 제공하지 않아 Apple Silicon에서 에뮬레이션으로 돌아가기 때문이며, 공식 저장소의 빌드 스크립트로 만든 멀티아키 이미지라 태그 체계와 동작이 같다. 운영은 amd64 EC2라 공식 이미지를 그대로 쓴다.

```bash
# 로컬 DB 정리 (데이터까지 삭제)
docker compose -f docker/docker-compose.local.yml down -v
```

## CI/CD

| 워크플로 | 트리거 | 동작 |
|----------|--------|------|
| `ci-pull-request.yml` | main 대상 PR | ktlintCheck + test (PR 코멘트 `빌드검증` 입력 시 전체 빌드 후 결과 코멘트) |
| `ci-push.yml` | main push | 전체 빌드 |
| `cicd-release.yml` | GitHub Release 발행 (vX.Y.Z) | bootJar → Docker 이미지 ECR push → EC2 SSH 배포 → 디스코드 알림 |

**배포는 릴리즈 태그 기준이다 (main 머지 ≠ 배포).** 릴리즈 발행 절차·버전 규칙·롤백은 [docs/RELEASE.md](docs/RELEASE.md), 브랜치·머지 규칙은 [docs/BRANCHING.md](docs/BRANCHING.md) 참고.
