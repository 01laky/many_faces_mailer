#!/usr/bin/env bash
# Starts the mailer-worker container for many_faces_mailer (dev compose).
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
docker compose -f docker-compose.yml up -d --build
