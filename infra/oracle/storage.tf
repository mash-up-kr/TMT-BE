# 미디어·DB 백업·tfstate 버킷. 앱과 스크립트는 S3 호환 API로 쓴다 —
# https://<namespace>.compat.objectstorage.<region>.oraclecloud.com, path-style, 고객 비밀 키.
# Always Free Object Storage는 합계 20GB다.

locals {
  namespace = data.oci_objectstorage_namespace.this.namespace
}

# 리뷰 사진 — 객체 공개 읽기. 버킷 목록 조회는 막혀 있다
resource "oci_objectstorage_bucket" "media" {
  compartment_id = var.tenancy_ocid
  namespace      = local.namespace
  name           = "tmt-media"
  access_type    = "ObjectRead"

  lifecycle {
    prevent_destroy = true
  }
}

resource "oci_objectstorage_bucket" "backup" {
  compartment_id = var.tenancy_ocid
  namespace      = local.namespace
  name           = "tmt-db-backup"
  access_type    = "NoPublicAccess"

  lifecycle {
    prevent_destroy = true
  }
}

# 덤프가 쌓여 무료 한도를 넘지 않게 보관 기간을 강제한다
resource "oci_objectstorage_object_lifecycle_policy" "backup" {
  namespace = local.namespace
  bucket    = oci_objectstorage_bucket.backup.name

  rules {
    name        = "expire-old-dumps"
    action      = "DELETE"
    is_enabled  = true
    time_amount = 30
    time_unit   = "DAYS"

    object_name_filter {
      inclusion_prefixes = ["pg/"]
    }
  }

  depends_on = [oci_identity_policy.objectstorage_lifecycle]
}

# 버저닝으로 state를 잘못 덮어써도 이전 판으로 되돌린다
resource "oci_objectstorage_bucket" "tfstate" {
  compartment_id = var.tenancy_ocid
  namespace      = local.namespace
  name           = "tmt-tfstate"
  access_type    = "NoPublicAccess"
  versioning     = "Enabled"

  lifecycle {
    prevent_destroy = true
  }
}

# 수명주기 규칙은 Object Storage 서비스가 대신 지우므로 서비스에 권한이 있어야 한다
resource "oci_identity_policy" "objectstorage_lifecycle" {
  compartment_id = var.tenancy_ocid
  name           = "tmt-objectstorage-lifecycle"
  description    = "Object Storage lifecycle rules may delete expired objects"
  statements     = ["Allow service objectstorage-${var.region} to manage object-family in tenancy"]
}
