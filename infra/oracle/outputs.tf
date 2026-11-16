output "public_subnet_id" {
  value = oci_core_subnet.public.id
}

output "was_public_ip" {
  value = oci_core_public_ip.was.ip_address
}

output "was_private_ip" {
  value = data.oci_core_private_ips.was.private_ips[0].ip_address
}

output "db_private_ip" {
  value = oci_core_instance.db.private_ip
}

output "app_image_repository" {
  value = "kix.ocir.io/${data.oci_objectstorage_namespace.this.namespace}/${oci_artifacts_container_repository.app.display_name}"
}
