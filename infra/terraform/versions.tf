terraform {
  required_version = ">= 1.10.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
  }

  # state는 로컬에 두지 않는다. DB 비밀번호가 state에 평문으로 남으므로
  # 암호화된 원격 백엔드가 필수다. (bootstrap/ 으로 버킷을 먼저 만들 것)
  #
  # use_lockfile: Terraform 1.10+ 의 S3 네이티브 락. DynamoDB 테이블이 필요 없다.
  backend "s3" {
    bucket       = "ttalkkak-tmt-tfstate"
    key          = "prod/infra.tfstate"
    region       = "ap-northeast-2"
    encrypt      = true
    use_lockfile = true
  }
}
