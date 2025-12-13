# Scaleway Serverless Container (transfer.sh)

Terraform to deploy a public transfer.sh instance that auto-deletes uploads after 5 hours.

## Prerequisites
- Terraform >= 1.5
- Scaleway credentials exported (`SCW_ACCESS_KEY`, `SCW_SECRET_KEY`, optional `SCW_PROJECT_ID`, `SCW_DEFAULT_REGION`, `SCW_DEFAULT_ZONE`)
- Docker Hub pull access (for `dutchcoders/transfer.sh:latest`)

## Usage
```bash
cd infra/scw-transfer
terraform init
terraform apply
```

Key variables (defaults shown in `variables.tf`):
- `purge_days` (default 1; transfer.sh only supports days, not hours)
- `purge_interval_hours` (default 1; transfer.sh expects hours)
- `max_upload_size` (default 51200; value is in kilobytes, ~50 MB)
- `registry_image` (override with your mirrored image)
- `storage_dir` (where uploads live inside the container)
- `min_scale` is pinned to 1 to keep files available for their lifetime.

Outputs:
- `transfer_base_url` – use this as the upload base URL in the Android app (`BuildConfig.TRANSFER_BASE_URL`).
- `transfer_domain` – raw Scaleway domain name of the container.

## Notes
- The transfer.sh CLI flags are set via the container `args` to enforce periodic purging. The binary only supports `--purge-days`, so the minimum retention is one day unless you customize the image.
- Privacy is `public` so anyone can upload; restrict at the edge (IP allowlist, auth proxy) if needed.
- The container uses the built-in local provider; uploads live on the instance filesystem and are cleaned by transfer.sh.
- Credentials: prefer exporting `SCW_ACCESS_KEY` / `SCW_SECRET_KEY` (and `SCW_PROJECT_ID`), but you can also fill `access_key` / `secret_key` in `terraform.tfvars` if you want to keep everything in the file (avoid committing secrets).
- Healthcheck hits `/` by default (transfer.sh returns 200 with the usage page). Adjust `healthcheck_path` if you expose a different status endpoint.
