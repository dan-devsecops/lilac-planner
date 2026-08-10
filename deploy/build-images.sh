#!/usr/bin/env bash
# =============================================================
#  Build & push the Lilac Planner images to ANY OCI registry.
#
#  Usage:
#    REGISTRY=myacr.azurecr.io ./deploy/build-images.sh
#    REGISTRY=123.dkr.ecr.eu-west-1.amazonaws.com TAG=v1.2.3 ./deploy/build-images.sh
#
#  Requires: docker (with buildx) logged in to $REGISTRY.
#  Set PLATFORMS for multi-arch, e.g. PLATFORMS=linux/amd64,linux/arm64
# =============================================================
set -euo pipefail

REGISTRY="${REGISTRY:?Set REGISTRY, e.g. REGISTRY=myacr.azurecr.io}"
TAG="${TAG:-latest}"
PLATFORMS="${PLATFORMS:-linux/amd64}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

build_push() {
  local name="$1" context="$2"
  local ref="${REGISTRY%/}/${name}:${TAG}"
  echo ">> building ${ref} (${PLATFORMS})"
  docker buildx build \
    --platform "${PLATFORMS}" \
    -t "${ref}" \
    --push \
    "${context}"
  echo ">> pushed ${ref}"
}

build_push "lilac-planner-backend"  "${ROOT}/backend"
build_push "lilac-planner-frontend" "${ROOT}/frontend"

echo
echo "Done. Deploy with:"
echo "  helm upgrade --install lilac ${ROOT}/deploy/helm/lilac-planner \\"
echo "    --set image.registry=${REGISTRY%/} \\"
echo "    --set backend.image.tag=${TAG} --set frontend.image.tag=${TAG}"
