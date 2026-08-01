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
# 1. 개발용 PostgreSQL 기동
docker compose -f docker/docker-compose.postgres.yml up -d

# 2. 애플리케이션 실행 (기본 프로필: dev)
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

## CI/CD

| 워크플로 | 트리거 | 동작 |
|----------|--------|------|
| `ci-pull-request.yml` | main 대상 PR | ktlintCheck + test (PR 코멘트 `빌드검증` 입력 시 전체 빌드 후 결과 코멘트) |
| `ci-push.yml` | main push | 전체 빌드 |
| `cicd-release.yml` | GitHub Release 발행 (vX.Y.Z) | bootJar → Docker 이미지 ECR push → EC2 SSH 배포 → 디스코드 알림 |

**배포는 릴리즈 태그 기준이다 (main 머지 ≠ 배포).** 릴리즈 발행 절차·버전 규칙·롤백은 [docs/RELEASE.md](docs/RELEASE.md), 브랜치·머지 규칙은 [docs/BRANCHING.md](docs/BRANCHING.md) 참고.
