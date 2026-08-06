# 릴리즈·배포 가이드 (Release & Deploy)

> 또맛또(TMT) 백엔드의 태그 기반 배포 절차. 브랜치·머지 규칙은 [BRANCHING.md](BRANCHING.md) 참고. (TMT-62)

## 1. 핵심 원칙: main 머지 ≠ 배포

**배포는 GitHub Release(릴리즈 태그)를 발행한 시점에만 일어난다.**

```
PR squash merge → main (배포 가능 상태 유지, 배포는 안 됨)
                    │
                    ▼  여러 PR을 모아서
GitHub Release 발행 (vX.Y.Z) ──→ cicd-release.yml 자동 트리거 ──→ 운영 서버 배포
```

- main은 항상 배포 가능한 상태를 유지하되, **실제 배포 타이밍은 태그로 통제**한다
- 급하지 않은 변경은 모았다가 한 번에 릴리즈한다

## 2. 버전 규칙 (semver)

태그 형식: `v<major>.<minor>.<patch>` — 예) `v0.3.1`

| 자리 | 올리는 경우 |
|------|------------|
| major | API 계약이 깨지는 변경 (클라이언트 수정 필요) |
| minor | 기능 추가 (하위 호환) |
| patch | 버그 수정, 설정 변경, 핫픽스 |

## 3. 릴리즈 절차

1. 배포할 변경들이 main에 머지되어 있고, main CI(빌드)가 초록불인지 확인
2. GitHub Release 발행 — 둘 중 편한 방법으로:

   **GitHub UI**: Releases → *Draft a new release* → 태그 `vX.Y.Z` 새로 생성(target: `main`) → *Generate release notes* → **Publish release**

   **gh CLI**:
   ```bash
   gh release create v0.1.0 --generate-notes
   ```
3. Publish 순간 [cicd-release.yml](../.github/workflows/cicd-release.yml)이 실행된다. Actions 탭에서 진행 상황 확인
4. 완료되면 디스코드 배포 알림(웹훅 설정 시)과 서버 헬스체크로 확인:
   ```bash
   curl http://<SERVER_HOST>:8080/api/health/db
   ```

> 릴리즈 노트는 자동 생성(Generate release notes)을 쓴다 — squash merge 덕에 PR 단위로 정리된다.
> **Draft 저장만 하면 배포되지 않는다.** 반대로 **Pre-release로 발행해도 배포된다**(워크플로가 published 이벤트로 트리거되므로 주의).

## 4. 파이프라인이 하는 일

| 단계 | 내용 |
|------|------|
| 빌드 | `./gradlew :tmt-bootstrap:bootJar` |
| 이미지 | Docker 이미지 빌드 → ECR에 `latest` 태그로 push |
| 전송 | `docker-compose.prod-postgres.yml`, `docker-compose.prod.yml`을 EC2 `~/tmt/`로 scp |
| DB | `tmt-postgres` 컨테이너가 **이미 실행 중이면 스킵** (데이터 볼륨 보존), 없으면 신규 기동 |
| 앱 | `tmt-app` 컨테이너를 매 배포마다 pull + `--force-recreate` 재기동 |
| 알림 | 성공 시 디스코드 웹훅으로 릴리즈 노트 전송 (미설정 시 스킵) |

### 필요한 Repository secrets

| Secret | 용도 |
|--------|------|
| `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` | ECR push 권한 IAM |
| `ECR_REGISTRY` | 예) `123456789012.dkr.ecr.ap-northeast-2.amazonaws.com` |
| `SERVER_HOST` / `SERVER_KEY` | 배포 대상 EC2 호스트, SSH 프라이빗 키 |
| `POSTGRES_PASSWORD` | 운영 PostgreSQL 비밀번호 |
| `DISCORD_WEBHOOK_RELEASE_URL` | (선택) 배포 알림 웹훅 |

## 5. 롤백

이미지를 `latest` 단일 태그로만 push하므로 **"이전 이미지로 되돌리기"는 불가능하다. 롤백 = 코드를 되돌려 새 패치 릴리즈를 발행(roll-forward)한다.**

1. 문제 PR을 revert (GitHub PR 화면의 *Revert* 버튼 → revert PR 머지)
2. 새 패치 버전으로 릴리즈 발행 — 예) `v0.3.1` 문제 시 `v0.3.2`

```bash
gh release create v0.3.2 --generate-notes
```

> 버전별 이미지 태그(`vX.Y.Z`)를 함께 push하도록 개선하면 이미지 단위 롤백이 가능해진다 — 필요해지면 릴리즈 워크플로에 태그 추가.

## 6. 주의사항

- prod는 `ddl-auto: validate` — **스키마 변경이 포함된 릴리즈는 DB 마이그레이션을 먼저 적용**해야 앱이 뜬다 (마이그레이션 도구 도입 전까지는 수동 적용)
- 시크릿은 절대 커밋하지 않는다 — 전부 Repository secrets로 주입 ([BRANCHING.md](BRANCHING.md) §7)
