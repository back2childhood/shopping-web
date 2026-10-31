#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
command -v openssl >/dev/null
umask 077
# Noclobber prevents accidentally replacing secrets used by existing volumes.
set -o noclobber
{
  printf 'POSTGRES_PASSWORD=%s\n' "$(openssl rand -hex 24)"
  printf 'JWT_SECRET=%s\n' "$(openssl rand -base64 48 | tr -d '\n')"
  printf 'INTERNAL_SERVICE_TOKEN=%s\n' "$(openssl rand -hex 32)"
} > .env.gcp
echo 'Created private .env.gcp. Do not commit, print, or regenerate it for an existing database.'
