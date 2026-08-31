# TMT 인프라 (Terraform)

또맛또 백엔드 AWS 인프라. **콘솔로 먼저 만들지 않고 여기서 정의한 뒤 apply한다** (TMT-62).

## 구성

```
        인터넷
          │
      ┌───┴────┐  IGW
      │  VPC 10.0.0.0/16  (ap-northeast-2a 단일 AZ)
      │
      ├── 10.0.1.0/24  was  ── EC2 t3.small  [EIP]  :8080
      │                            │
      │                            │ 5432 (SG 참조로만 허용)
      │                            ▼
      └── 10.0.2.0/24  db   ── EC2 t3.small
                                   └── EBS gp3 20GiB  /mnt/pgdata  (prevent_destroy)
                                        └── docker: postgis/postgis:16-3.4
```

- **RDS를 쓰지 않는다** — PostgreSQL은 DB 인스턴스의 도커 컨테이너로 돈다 (TMT-61)
- **NAT Gateway·ALB·Multi-AZ 없음** — 비용 방침 (TMT-61)
- **WAS/DB 분리 2대 구성** — 재배포가 DB에 영향을 주지 않고 메모리도 분리된다

## 사전 준비

**AWS 자격증명만 있으면 된다** (`aws configure` 또는 `AWS_PROFILE`). state는 S3에 있고 변수는
기본값이 채워져 있어서, 받아서 바로 `init` → `plan`이 된다.

```bash
aws sts get-caller-identity   # 393286882141 계정인지 확인
```

state 버킷(`ttalkkak-tmt-tfstate`)은 이미 만들어져 있다. `bootstrap/`은 **최초 1회용이라 다시
실행하지 않는다** — 이미 있는 버킷에 대고 돌리면 안 된다.

## 실행

```bash
terraform init
terraform plan     # 리뷰 후
terraform apply
```

**`plan`이 `No changes`로 나오는 것이 정상이다.** 뭔가 잡히면 둘 중 하나다 — 내가 방금 코드를
고쳤거나, **콘솔에서 직접 바꾼 것이 코드에 안 들어와 있거나**(drift). 후자면 코드부터 맞춘 뒤에
apply한다. 자기 변경이 아닌 것이 plan에 섞여 있으면 그대로 나가므로 반드시 전부 읽는다.

## 여럿이 함께 쓸 때

state는 S3 원격 백엔드에 있고 **락이 걸린다**(`use_lockfile = true`). 누군가 apply 중이면
다른 사람은 락 대기로 막히므로, 동시에 두 명이 밀어 넣는 사고는 나지 않는다.

- **누가 언제 바꿨는지**는 버킷 버저닝으로 남는다 (`prod/infra.tfstate`)
- 락이 안 풀린 채 프로세스가 죽었다면 `terraform force-unlock <ID>` — **상대가 실제로 안 돌리는 것을
  확인한 뒤에만** 쓴다
- 인프라 변경은 코드 리뷰를 거친다. `apply`는 머지 전후 어느 쪽이든 좋지만, **apply한 사람이 PR에
  plan 결과를 남긴다** (TMT-252에서 "1 to change"로 적힌 것이 실제로는 5건이었던 적이 있다)

### 변수를 개인적으로 덮고 싶다면

`terraform.tfvars`는 `.gitignore` 대상이고 **없어도 동작한다**. 기본값과 다르게 쓰고 싶을 때만 만든다.

```bash
cp terraform.tfvars.example terraform.tfvars
```

> 기본값에 들어 있는 `ssh_public_key`는 **공개키라 시크릿이 아니다.** 예전에는 이 값이 개인
> tfvars에만 있어서, 그 파일을 가진 사람만 apply할 수 있었고 실제 구성이 코드에 안 남아 있었다.

## 접속

SSH는 기본적으로 **전면 차단**(`was_ssh_cidrs = []` · `db_ssh_cidrs = []`)이다. 두 변수는 분리돼 있어서, 비상시 WAS에 본인 IP를 열어도 DB의 22번은 닫힌 채로 남는다. DB 서브넷은 퍼블릭이라 그 노출이 곧 인터넷 노출이므로 `db_ssh_cidrs`는 빈 목록을 유지한다.

**SSM은 편의 수단이 아니라 부팅 하드 의존이다.** `db.sh.tftpl`이 SSM에서 DB 비밀번호를 읽어오지 못하면 PostgreSQL이 아예 뜨지 않는다(재시도 30회 후 실패 처리). 아웃바운드를 조이거나 인스턴스 역할 권한을 줄일 때 이 경로를 먼저 확인해야 한다.

접속:

```bash
aws ssm start-session --target $(terraform output -json instance_ids | jq -r .was)
```

DB 비밀번호:

```bash
aws ssm get-parameter --name "$(terraform output -raw db_password_ssm_parameter)" \
  --with-decryption --query Parameter.Value --output text
```

## 알아둘 것

**state에 DB 비밀번호가 평문으로 들어간다.** S3 백엔드(`encrypt = true`)가 전제이고, 로컬 state 파일은 `.gitignore` 대상이다. `*.tfplan`도 마찬가지다.

**DB 비밀번호는 최초 `initdb`에만 적용된다.** postgres 엔트리포인트는 `POSTGRES_PASSWORD_FILE`을 PGDATA가 비어 있을 때만 읽는다. 데이터 볼륨은 영속되므로 `initdb`는 최초 1회만 돈다.

따라서 **SSM 값만 바꿔도 실제 DB 비밀번호는 바뀌지 않는다.** 회전하려면 두 곳을 함께 고쳐야 한다:

```bash
# 1) 컨테이너 안에서 실제 비밀번호를 바꾼다
docker exec postgres bash -c \
  'PGPASSWORD=$(cat /run/secrets/db_password) psql -U tmt -d tmt \
     -c "ALTER USER tmt PASSWORD '"'"'<새 비밀번호>'"'"'"'
# 2) SSM 파라미터도 같은 값으로 갱신한다
aws ssm put-parameter --name /tmt-prod/db/password --type SecureString \
  --value '<새 비밀번호>' --overwrite
```

`random_password.db`에는 데이터 볼륨 ID를 `keepers`로 걸어뒀다. 볼륨이 살아 있는 한 Terraform이 비밀번호를 재생성하지 않으므로, state 유실·`taint`·리소스 교체로 SSM과 실제 DB가 어긋나 락아웃되는 상황을 막는다. 볼륨을 새로 만드는 경우에만 새 비밀번호가 나오고, 그때는 `initdb`도 다시 돌아 짝이 맞는다.

**DB 데이터 볼륨은 `terraform destroy`로 지워지지 않는다.** `prevent_destroy = true`가 걸려 있다. 정말 지우려면 그 설정을 먼저 제거해야 한다. 의도적인 마찰이다.

**DB 서브넷은 퍼블릭이다.** NAT Gateway를 안 쓰기로 한 이상 아웃바운드 경로는 IGW뿐이다. VPC 인터페이스 엔드포인트라는 선택지도 있지만, 필요한 것만 세도 ECR 2개 + SSM 3개 + KMS 1개로 6개이고 엔드포인트당 시간 과금이 붙어 월 $45 정도가 된다. NAT Gateway와 비슷한 금액이라 실익이 없어서 쓰지 않는다. 인바운드는 보안그룹에서 WAS 보안그룹 소스의 5432만 허용하므로 외부에서 DB에 직접 닿을 수는 없다. 나중에 NAT를 도입하면 `db` 서브넷의 라우팅 테이블만 갈아끼우면 된다 — 그러라고 서브넷을 갈라뒀다.

## 백업 · 복구

RDS 자동 스냅샷이 없으므로 `pg_dump`를 직접 돌린다. EBS 분리(`prevent_destroy`)는 인스턴스 교체를 견디게 해줄 뿐이고, 볼륨이 깨지거나 데이터를 잘못 지운 경우의 복구 수단은 이 덤프뿐이다.

- DB 인스턴스의 systemd 타이머가 하루 1회(기본 KST 03:00) `pg_dump -Fc` → S3 업로드
- 경로: `s3://<backup_bucket_name>/pg/YYYY/MM/tmt-<타임스탬프>.dump`
- 보관 30일 (`backup_retention_days`), 이후 S3 lifecycle이 삭제
- 인스턴스 역할에는 **쓰기 권한만**(`s3:PutObject` + 멀티파트 abort) 준다. 삭제 권한이 없어 실수나 스크립트 오작동으로 기존 백업이 지워지지는 않는다 (같은 키로 덮어쓰는 것까지 막지는 못한다)

동작 확인·수동 실행:

```bash
aws ssm start-session --target $(terraform output -json instance_ids | jq -r .db)
systemctl list-timers tmt-pg-backup.timer
sudo /usr/local/bin/tmt-pg-backup.sh      # 즉시 1회
journalctl -u tmt-pg-backup.service -n 50
```

복구:

```bash
aws s3 cp s3://<bucket>/pg/2026/08/tmt-<ts>.dump /var/tmp/restore.dump
docker cp /var/tmp/restore.dump postgres:/tmp/restore.dump
docker exec -it postgres bash -c \
  'PGPASSWORD=$(cat /run/secrets/db_password) pg_restore -U tmt -d tmt --clean --if-exists /tmp/restore.dump'
```

> `--clean`은 기존 객체를 지우고 덮어쓴다. 운영 DB에 그대로 쏘기 전에 빈 DB를 만들어 먼저 확인할 것.

## 배포와의 연결 (TMT-107)

- **ECR** — 앱 이미지 리포지토리 `tmt`(`ecr.tf`). CI가 push하고 WAS가 인스턴스 역할(`ecr_pull`)로 pull한다
- **CI 자격** — 배포 IAM 사용자에 붙일 정책이 `ci.tf`에 있다. 사용자 자체는 state에 두지 않으므로 부착은 수동: `aws iam attach-user-policy --user-name <ci-user> --policy-arn "$(terraform output -raw ci_deploy_policy_arn)"`
- **배포 경로** — `cicd-release.yml`이 SSM Run Command로 WAS에 명령을 보낸다. SSH 전면 차단과 충돌하지 않고, 인스턴스는 `Name=tmt-prod-was` 태그로 찾는다
- **DB 접속 정보** — 정본은 SSM 파라미터 `/tmt-prod/db/*`. 배포 시 WAS가 읽어 앱 컨테이너에 주입하므로 GitHub Secrets에 비밀번호가 없다

## 아직 없는 것

- **복구 리허설** — 위 백업·복구 절차를 실제로 한 번 돌려봐야 백업이 있다고 말할 수 있다 (TMT-106, 스키마 v1 투입 후)
