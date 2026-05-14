#!/usr/bin/env bash
# Stops the mailer-worker compose stack for many_faces_mailer.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
docker compose -f docker-compose.yml down
