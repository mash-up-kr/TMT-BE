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

> **임시**: mock 개발 기간에는 main 머지 시 자동 배포가 함께 동작한다 ([ci-push.yml](../.github/workflows/ci-push.yml)의 `deploy` job). 위 정식 릴리즈 정책은 그대로이며, mock 기간이 끝나면 해당 job을 제거한다.

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
4. 완료되면 디스코드 배포 알림(웹훅 설정 시)과 서버 헬스체크로 확인 — 워크플로 자체도 마지막에 같은 헬스체크를 수행한다:
   ```bash
   curl https://129-225-153-130.sslip.io/api/health/db   # 실도메인 전환(TMT-446) 뒤에는 api.ttomatto.kr
   ```

> 릴리즈 노트는 자동 생성(Generate release notes)을 쓴다 — squash merge 덕에 PR 단위로 정리된다.
> **Draft 저장만 하면 배포되지 않는다.** 반대로 **Pre-release로 발행해도 배포된다**(워크플로가 published 이벤트로 트리거되므로 주의).

## 4. 파이프라인이 하는 일

Oracle Cloud 오사카 2대 구성(WAS/DB 분리, `infra/oracle`) 기준이다. 이미지는 OCIR, 서버 접속은 배포 전용 키의 SSH다 (TMT-445).

| 단계 | 내용 |
|------|------|
| 빌드 | `./gradlew :tmt-bootstrap:bootJar` |
| 이미지 | Docker 이미지 빌드 → OCIR(`kix.ocir.io/axrpyc6zylkj/tmt`)에 `vX.Y.Z`·`latest` 두 태그로 push |
| 배포 | SSH로 compose·Caddyfile·`scripts/deploy/remote-deploy.sh`를 `/opt/tmt`에 올린 뒤 실행: Vault 시크릿을 읽어 `.env` 생성 → 필수 값 검사 → `vX.Y.Z` 이미지 pull → `--force-recreate` 재기동 |
| DB | **건드리지 않는다** — PostgreSQL은 DB 인스턴스 소관 (`infra/oracle/provision`) |
| 헬스체크 | 서버 안에서 `localhost:8080/api/health/db`를 최대 5분, 이어서 HTTPS 경로를 최대 3분 기다린다. E2.1.Micro는 기동에 90초 안팎이 걸린다 |
| 알림 | 성공 시 디스코드 웹훅으로 릴리즈 노트 전송 (미설정 시 스킵) |

앱 시크릿의 **정본은 Vault 시크릿 `tmt-prod-env`**(`.env` 전체, `infra/oracle/vault.tf`) 하나다. WAS가 자기 인스턴스 권한(동적 그룹 `tmt-was`)으로 배포 시점에 읽는다. 값을 바꾸려면 콘솔이나 CLI로 시크릿의 새 버전을 만들고 재배포한다:

```bash
oci vault secret update-base64 --secret-id "$(cd infra/oracle && terraform output -raw app_env_secret_id)" \
  --secret-content-content "$(base64 < new.env)"
```

### 필요한 Repository secrets

| Secret | 용도 |
|--------|------|
| `OCIR_USERNAME` / `OCIR_AUTH_TOKEN` | OCIR 로그인 — `<namespace>/<콘솔 사용자명>`과 그 사용자의 인증 토큰 |
| `WAS_HOST` | WAS 예약 공인 IP (`terraform output -raw was_public_ip`) |
| `WAS_SSH_KEY` / `WAS_SSH_KNOWN_HOSTS` | 배포 전용 SSH 개인 키(ubuntu 계정)와 `ssh-keyscan`으로 받은 호스트 키 |
| `DISCORD_WEBHOOK_RELEASE_URL` | (선택) 배포 알림 웹훅 |

## 5. 롤백

기본은 **코드를 되돌려 새 패치 릴리즈를 발행(roll-forward)**한다. 급할 때는 이전 버전 이미지(`vX.Y.Z` 태그)로 바로 되돌릴 수 있다 — 아래 3번.

1. 문제 PR을 revert (GitHub PR 화면의 *Revert* 버튼 → revert PR 머지)
2. 새 패치 버전으로 릴리즈 발행 — 예) `v0.3.1` 문제 시 `v0.3.2`

```bash
gh release create v0.3.2 --generate-notes
```

3. 이미지 단위로 즉시 되돌리기 — WAS에서 이전 태그로 배포 스크립트를 다시 돌린다:

```bash
ssh ubuntu@<WAS> "sudo bash /opt/tmt/remote-deploy.sh kix.ocir.io/axrpyc6zylkj/tmt:v0.3.1 rollback <app_env_secret_id>"
```

## 6. 주의사항

- prod는 `ddl-auto: validate` — **스키마 변경이 포함된 릴리즈는 DB 마이그레이션을 먼저 적용**해야 앱이 뜬다 (마이그레이션 도구 도입 전까지는 수동 적용)
- 시크릿은 절대 커밋하지 않는다 — 전부 Repository secrets로 주입 ([BRANCHING.md](BRANCHING.md) §7)
