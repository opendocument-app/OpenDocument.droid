variable "project_id" {
  description = "Scaleway project ID (can also be set via SCW_PROJECT_ID env var)."
  type        = string
  default     = null
}

variable "access_key" {
  description = "Scaleway access key (prefer env var SCW_ACCESS_KEY)."
  type        = string
  default     = null
}

variable "secret_key" {
  description = "Scaleway secret key (prefer env var SCW_SECRET_KEY)."
  type        = string
  default     = null
}

variable "region" {
  description = "Scaleway region for the container namespace."
  type        = string
  default     = "fr-par"
}

variable "zone" {
  description = "Scaleway zone; required by some APIs even if containers are regional."
  type        = string
  default     = "fr-par-1"
}

variable "namespace_name" {
  description = "Container namespace name."
  type        = string
  default     = "transfer-sh"
}

variable "container_name" {
  description = "Container name."
  type        = string
  default     = "transfer-sh"
}

variable "registry_image" {
  description = "Docker image for transfer.sh."
  type        = string
  default     = "docker.io/dutchcoders/transfer.sh:v1.6.1"
}

variable "purge_interval_hours" {
  description = "Interval between purge runs (hours; transfer.sh expects hours)."
  type        = number
  default     = 1
}

variable "max_upload_size" {
  description = "Maximum upload size allowed by transfer.sh in kilobytes (e.g., 51200 for ~50 MB)."
  type        = number
  default     = 51200
}

variable "purge_days" {
  description = "Number of days before uploads are purged (integer, transfer.sh flag). Minimum supported is 1."
  type        = number
  default     = 1
}

variable "storage_dir" {
  description = "Path inside the container where uploads are stored."
  type        = string
  default     = "/data"
}

variable "port" {
  description = "Port the container listens on."
  type        = number
  default     = 8080
}

variable "max_scale" {
  description = "Maximum number of container instances."
  type        = number
  default     = 1
}

variable "min_scale" {
  description = "Minimum number of container instances."
  type        = number
  default     = 1
}

variable "healthcheck_path" {
  description = "HTTP path for container healthcheck."
  type        = string
  default     = "/"
}

variable "cpu_limit_millicpu" {
  description = "CPU limit in millicpu."
  type        = number
  default     = 350
}

variable "memory_limit_mb" {
  description = "Memory limit in MB."
  type        = number
  default     = 512
}

variable "environment_variables" {
  description = "Plain environment variables for the container."
  type        = map(string)
  default = {
    TMPDIR = "/data"
  }
}
