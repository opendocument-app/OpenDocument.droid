# Scaleway credentials and deployment settings for transfer.sh
# Fill in the project ID and adjust names/limits as needed.

project_id            = "81e43e7c-903e-4aaa-a98d-479f47c3138d"
access_key            = "SCWWZCKQA94245AYRR4H"    # or leave unset to use SCW_ACCESS_KEY env var
secret_key            = "958975f3-9e79-439b-9de7-3658409cbf2c"    # or leave unset to use SCW_SECRET_KEY env var
region                = "nl-ams"
zone                  = "nl-ams-1"

namespace_name        = "transfer-sh"
container_name        = "transfer-sh"

registry_image        = "rg.nl-ams.scw.cloud/funcscwtransfershxuil1jyq/transfer-sh:v1.6.1"

purge_days            = 1
purge_interval_hours  = 1
max_upload_size       = 51200
storage_dir           = "/data"
port                  = 8080
cpu_limit_millicpu    = 350
memory_limit_mb       = 512
min_scale             = 1
max_scale             = 1
healthcheck_path      = "/"

environment_variables = {
  TMPDIR = "/data"
}
