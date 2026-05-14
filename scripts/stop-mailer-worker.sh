#!/usr/bin/env bash
# Stops the mailer-worker compose stack for many_faces_mailer.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
docker compose -f docker-compose.yml down
# TLS smoke stack (same project name as smoke-grpc-tls.sh / clear-all-dev.sh).
docker compose -f docker-compose.tls-smoke.yml -p mf-mailer-tls-smoke down -v 2>/dev/null || true
