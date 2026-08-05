# state를 담을 S3 버킷을 만드는 단계. 닭-달걀 문제 때문에 본체와 분리한다
# (백엔드 버킷을 원격 state로 관리할 수는 없다).
#
# 최초 1회만 로컬 state로 apply하고, 그 뒤로는 건드릴 일이 없다.
# 이 디렉토리의 terraform.tfstate 는 커밋하지 않는다 — 버킷 메타데이터뿐이고
# 실제 인프라 state는 여기 없다.
#
# 다만 "유실돼도 재생성 가능"은 아니다. 로컬 state를 잃어도 AWS에는 버킷이 남아 있어서
# apply를 다시 돌리면 BucketAlreadyOwnedByYou 로 실패한다. 그때는 import로 복구한다:
#
#   terraform import aws_s3_bucket.state <버킷명>
#
# 버킷에 prevent_destroy가 걸려 있어 지우고 다시 만드는 경로도 막혀 있다.

terraform {
  required_version = ">= 1.10.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
  }
}

provider "aws" {
  region = var.region

  default_tags {
    tags = {
      Project   = "TMT"
      ManagedBy = "terraform"
    }
  }
}

variable "region" {
  type    = string
  default = "ap-northeast-2"
}

variable "bucket_name" {
  description = <<-EOT
    state 버킷 이름. versions.tf 의 backend 설정과 반드시 같아야 한다.

    S3 버킷 이름은 전 계정 공용이라 흔한 이름은 이미 선점되어 있다
    (`tmt-terraform-state`는 CreateBucket에서 BucketAlreadyExists로 실패했다).
    HeadBucket은 권한 없는 기존 버킷에도 404를 주므로 사전 확인 수단이 못 된다 —
    충돌하면 이 값과 versions.tf 를 함께 더 유니크한 이름으로 바꾼다.
  EOT
  type        = string
  default     = "ttalkkak-tmt-tfstate"
}

resource "aws_s3_bucket" "state" {
  bucket = var.bucket_name

  lifecycle {
    prevent_destroy = true
  }
}

# state에는 DB 비밀번호가 평문으로 들어간다. 버저닝은 실수로 덮어썼을 때의
# 복구 수단이고, 암호화·퍼블릭 차단은 최소 요건이다.
resource "aws_s3_bucket_versioning" "state" {
  bucket = aws_s3_bucket.state.id

  versioning_configuration {
    status = "Enabled"
  }
}

# SSE-S3(AES256)를 쓴다. SSE-KMS(고객 관리 키)로 두면 s3:GetObject 외에 kms:Decrypt가
# 한 겹 더 필요해져서, state에 든 평문 DB 비밀번호에 접근 통제가 하나 더 붙는다.
# 그럼에도 AES256을 택한 이유는 고객 관리 키가 키당 월 $1이고, NAT·ALB·Multi-AZ를
# 전부 걷어낸 비용 방침(TMT-61)과 맞지 않기 때문이다.
# 실유저 데이터가 들어오는 시점(UT2 8/29 전후)에 재검토한다.
resource "aws_s3_bucket_server_side_encryption_configuration" "state" {
  bucket = aws_s3_bucket.state.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_public_access_block" "state" {
  bucket = aws_s3_bucket.state.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

output "bucket" {
  value = aws_s3_bucket.state.id
}
