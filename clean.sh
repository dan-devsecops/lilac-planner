#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"

echo "Cleaning backend..."
cd "$ROOT/backend" && mvn clean -q

echo "Cleaning frontend..."
rm -rf "$ROOT/frontend/dist" "$ROOT/frontend/node_modules"

echo "Cleaning Terraform..."
for provider in aws azure gcp; do
  rm -rf "$ROOT/deploy/terraform/$provider/.terraform" \
         "$ROOT/deploy/terraform/$provider/.terraform.lock.hcl"
done

echo "Done."
