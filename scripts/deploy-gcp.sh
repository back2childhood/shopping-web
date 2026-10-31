#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

if [[ ! -f .env.gcp ]]; then
  echo 'Missing .env.gcp. Run: bash scripts/init-gcp-env.sh' >&2
  exit 1
fi

compose=(sudo docker compose --project-name shopping-gcp --env-file .env.gcp -f compose.yml -f compose.gcp.yml)
"${compose[@]}" config --quiet
# Build before starting databases: the 8 GB VM has limited memory.
# The same command safely reuses build caches on subsequent releases.
"${compose[@]}" --parallel 1 build
"${compose[@]}" up -d --wait --wait-timeout 900
"${compose[@]}" ps
curl --fail --silent --show-error http://127.0.0.1:8088/healthz
curl --fail --silent --show-error --output /dev/null http://127.0.0.1:8088/login
echo 'Web entry ready on VM loopback port 8088. Use an SSH tunnel; no public ports were opened.'
