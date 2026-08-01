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

1. AWS 자격증명 (`aws configure` 또는 `AWS_PROFILE`)
2. state 버킷 최초 1회 생성:
   ```bash
   cd bootstrap && terraform init && terraform apply
   ```
3. 변수 파일:
   ```bash
   cp terraform.tfvars.example terraform.tfvars   # ssh_public_key 필수
   ```

## 실행

```bash
terraform init
terraform plan     # 리뷰 후
terraform apply
```

## 접속

SSH는 기본적으로 **전면 차단**(`ssh_allowed_cidrs = []`)이다. SSM Session Manager를 쓴다:

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

**DB 데이터 볼륨은 `terraform destroy`로 지워지지 않는다.** `prevent_destroy = true`가 걸려 있다. 정말 지우려면 그 설정을 먼저 제거해야 한다. 의도적인 마찰이다.

**DB 서브넷은 퍼블릭이다.** NAT Gateway를 안 쓰기로 한 이상 이미지 pull·패치·SSM 연결의 아웃바운드 경로가 IGW뿐이다. 인바운드는 보안그룹에서 WAS 보안그룹 소스의 5432만 허용하므로 외부에서 DB에 직접 닿을 수는 없다. 나중에 NAT를 도입하면 `db` 서브넷의 라우팅 테이블만 갈아끼우면 된다 — 그러라고 서브넷을 갈라뒀다.

## 아직 없는 것

이 디렉토리는 TMT-62 체크리스트 중 **리소스 코드화까지**만 덮는다. 남은 항목:

- **백업** — `pg_dump` 주기 실행 + S3 보관 (TMT-61). RDS 자동 스냅샷이 없으므로 이게 없으면 인스턴스와 함께 데이터가 사라진다
- **배포 CI** — 릴리즈 태그 트리거 GitHub Actions (TMT-62)
- **릴리즈 가이드** — 배포·롤백 절차 문서화 (TMT-62)
