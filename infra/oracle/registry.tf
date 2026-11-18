# 앱 이미지 저장소 — ECR 대체. 주소는 kix.ocir.io/<namespace>/tmt (kix = 오사카).
# 로그인 사용자명은 <namespace>/<콘솔 사용자명>, 비밀번호는 사용자 인증 토큰이다.
resource "oci_artifacts_container_repository" "app" {
  compartment_id = var.tenancy_ocid
  display_name   = "tmt"
  is_public      = false
}

data "oci_objectstorage_namespace" "this" {
  compartment_id = var.tenancy_ocid
}
