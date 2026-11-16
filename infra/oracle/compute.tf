# 두 인스턴스는 자원 부족 때문에 재시도 스크립트(CLI)로 먼저 잡았고 여기로 import했다.
# 다시 만들면 자리를 잃으므로, 생성 시점 값이 바뀌어도 교체되지 않게 ignore_changes로 묶는다.
# 런타임 준비(Docker·스왑·컨테이너)는 user_data가 아니라 provision/ 스크립트로 SSH 적용한다.

import {
  to = oci_core_instance.was
  id = "ocid1.instance.oc1.ap-osaka-1.anvwsljraj6wb6ack5sbbayuwm4uobhc3ob5ixwc25siomz5gucdlbgr22ma"
}

import {
  to = oci_core_instance.db
  id = "ocid1.instance.oc1.ap-osaka-1.anvwsljraj6wb6acwz5hlj3js5hpujs235y2v4vgzt6erh6llfocyqlqihrq"
}

resource "oci_core_instance" "was" {
  compartment_id      = var.tenancy_ocid
  availability_domain = var.availability_domain
  shape               = "VM.Standard.E2.1.Micro"
  display_name        = "tmt-micro-1"

  create_vnic_details {
    subnet_id = oci_core_subnet.public.id
  }

  source_details {
    source_type = "image"
    source_id   = var.x86_image_id
  }

  lifecycle {
    prevent_destroy = true
    ignore_changes  = [create_vnic_details, source_details, metadata, defined_tags, display_name]
  }
}

resource "oci_core_instance" "db" {
  compartment_id      = var.tenancy_ocid
  availability_domain = var.availability_domain
  shape               = "VM.Standard.E2.1.Micro"
  display_name        = "tmt-micro-2"

  create_vnic_details {
    subnet_id = oci_core_subnet.public.id
  }

  source_details {
    source_type = "image"
    source_id   = var.x86_image_id
  }

  lifecycle {
    prevent_destroy = true
    ignore_changes  = [create_vnic_details, source_details, metadata, defined_tags, display_name]
  }
}

# DNS A 레코드가 가리킬 주소. 임시 공인 IP는 인스턴스를 다시 만들면 바뀐다.
# 붙이기 전에 WAS VNIC의 임시 공인 IP를 먼저 떼야 한다 (VNIC당 공인 IP 1개).
data "oci_core_vnic_attachments" "was" {
  compartment_id = var.tenancy_ocid
  instance_id    = oci_core_instance.was.id
}

data "oci_core_private_ips" "was" {
  vnic_id = data.oci_core_vnic_attachments.was.vnic_attachments[0].vnic_id
}

resource "oci_core_public_ip" "was" {
  compartment_id = var.tenancy_ocid
  lifetime       = "RESERVED"
  display_name   = "tmt-prod-was"
  private_ip_id  = data.oci_core_private_ips.was.private_ips[0].id

  lifecycle {
    prevent_destroy = true
  }
}

# DB 데이터는 부트 볼륨과 분리한다 — 인스턴스를 갈아엎어도 데이터가 남는다.
# Always Free 블록 스토리지는 합계 200GB다 (부트 47GB × 2 + 이 볼륨).
resource "oci_core_volume" "db_data" {
  compartment_id      = var.tenancy_ocid
  availability_domain = var.availability_domain
  display_name        = "tmt-prod-db-data"
  size_in_gbs         = 50

  lifecycle {
    prevent_destroy = true
  }
}

resource "oci_core_volume_attachment" "db_data" {
  attachment_type = "paravirtualized"
  instance_id     = oci_core_instance.db.id
  volume_id       = oci_core_volume.db_data.id
  device          = "/dev/oracleoci/oraclevdb"
}
