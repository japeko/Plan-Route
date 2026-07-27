#!/usr/bin/env bash
# Builds the client and server Docker images and pushes them to GitHub
# Container Registry (ghcr.io) — the "distribution packets" that get
# pulled and run on the cloud host, without needing this source repo there.
#
# Usage:
#   GITHUB_OWNER=your-github-username ./scripts/release-images.sh [tag]
#
# [tag] defaults to the current git commit short hash. Images are always
# also tagged "latest".
#
# One-time setup before running this:
#   1. Create a GitHub Personal Access Token (classic) with the
#      write:packages scope: https://github.com/settings/tokens
#   2. docker login ghcr.io -u <your-github-username>
#      (paste the token as the password when prompted)

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

if [[ -z "${GITHUB_OWNER:-}" ]]; then
  echo "Error: GITHUB_OWNER is not set." >&2
  echo "Usage: GITHUB_OWNER=your-github-username $0 [tag]" >&2
  exit 1
fi

TAG="${1:-$(git rev-parse --short HEAD)}"
REGISTRY="ghcr.io/${GITHUB_OWNER}"

build_and_push() {
  local name="$1"
  local dockerfile="$2"
  local image="${REGISTRY}/${name}"

  echo "==> Building ${image}:${TAG}"
  docker build -f "$dockerfile" -t "${image}:${TAG}" -t "${image}:latest" .

  echo "==> Pushing ${image}:${TAG} and :latest"
  docker push "${image}:${TAG}"
  docker push "${image}:latest"
}

build_and_push poi-client packages/client/Dockerfile
build_and_push poi-server packages/server/Dockerfile

echo
echo "Done. Images published:"
echo "  ${REGISTRY}/poi-client:${TAG} (and :latest)"
echo "  ${REGISTRY}/poi-server:${TAG} (and :latest)"
echo
echo "On the cloud host, set IMAGE_TAG=${TAG} in deploy/.env to deploy this exact build."
