# 운영 시크릿 — WAS의 /opt/tmt/.env 전체를 시크릿 하나에 담는다 (TMT-445).
# 배포 스크립트가 WAS의 인스턴스 권한으로 읽어 .env를 새로 쓴다. 값 변경은 콘솔·CLI로
# 시크릿의 새 버전을 만들고 재배포하면 된다.
#
# 실제 값은 Terraform에 두지 않는다 — 처음엔 자리표시 값으로 만들고 CLI로 덮어쓴다.
# 그래서 내용 변경을 ignore_changes로 무시한다.

resource "oci_kms_vault" "main" {
  compartment_id = var.tenancy_ocid
  display_name   = "tmt-prod"
  vault_type     = "DEFAULT"
}

resource "oci_kms_key" "secrets" {
  compartment_id      = var.tenancy_ocid
  display_name        = "tmt-prod-secrets"
  management_endpoint = oci_kms_vault.main.management_endpoint
  protection_mode     = "SOFTWARE"

  key_shape {
    algorithm = "AES"
    length    = 32
  }
}

resource "oci_vault_secret" "app_env" {
  compartment_id = var.tenancy_ocid
  vault_id       = oci_kms_vault.main.id
  key_id         = oci_kms_key.secrets.id
  secret_name    = "tmt-prod-env"

  secret_content {
    content_type = "BASE64"
    content      = base64encode("# set via CLI")
  }

  lifecycle {
    ignore_changes = [secret_content]
  }
}

resource "oci_identity_dynamic_group" "was" {
  compartment_id = var.tenancy_ocid
  name           = "tmt-was"
  description    = "TMT WAS instance"
  matching_rule  = "instance.id = '${oci_core_instance.was.id}'"
}

resource "oci_identity_policy" "was_read_env" {
  compartment_id = var.tenancy_ocid
  name           = "tmt-was-read-env"
  description    = "WAS reads the app env secret at deploy time"
  statements = [
    "Allow dynamic-group tmt-was to read secret-bundles in tenancy where target.secret.id = '${oci_vault_secret.app_env.id}'",
  ]
}
