terraform {
  required_version = ">= 1.5.0"

  required_providers {
    scaleway = {
      source  = "scaleway/scaleway"
      version = "~> 2.36"
    }
  }
}

provider "scaleway" {
  project_id = var.project_id
  region     = var.region
  zone       = var.zone
  access_key = var.access_key
  secret_key = var.secret_key
}

resource "scaleway_container_namespace" "transfer" {
  name        = var.namespace_name
  description = "Temporary uploads for OpenDocument Reader via transfer.sh"
  region      = var.region
}

resource "scaleway_container" "transfer" {
  name          = var.container_name
  namespace_id  = scaleway_container_namespace.transfer.id
  registry_image = var.registry_image

  min_scale = var.min_scale
  max_scale = var.max_scale

  memory_limit = var.memory_limit_mb
  cpu_limit    = var.cpu_limit_millicpu

  port      = var.port
  privacy   = "public"
  protocol  = "http1"
  timeout   = 300
  deploy    = true

  environment_variables = var.environment_variables

  health_check {
    failure_threshold = 3
    interval          = "30s"

    http {
      path = var.healthcheck_path
    }
  }

  args = [
    "--provider", "local",
    "--basedir", var.storage_dir,
    "--listener", ":${var.port}",
    "--purge-days", var.purge_days,
    "--purge-interval", var.purge_interval_hours,
    "--max-upload-size", var.max_upload_size,
  ]
}

output "transfer_domain" {
  value = scaleway_container.transfer.domain_name
}

output "transfer_base_url" {
  value = "https://${scaleway_container.transfer.domain_name}"
}
