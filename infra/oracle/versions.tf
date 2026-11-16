terraform {
  required_version = ">= 1.10.0"

  required_providers {
    oci = {
      source  = "oracle/oci"
      version = "~> 7.0"
    }
  }

  # state에는 DB 볼륨·IP 등 자원 식별자가 담긴다. 버저닝된 tmt-tfstate 버킷에 둔다.
  # 인증은 provider와 같은 ~/.oci/config(DEFAULT)다.
  backend "oci" {
    bucket    = "tmt-tfstate"
    namespace = "axrpyc6zylkj"
    key       = "prod/infra.tfstate"
    region    = "ap-osaka-1"
  }
}

provider "oci" {
  region = var.region
}
