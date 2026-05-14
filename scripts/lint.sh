#!/usr/bin/env bash
# Lint many_faces_mailer — compile main + tests (requires many_faces_proto submodule).
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

echo "🔍 Linting many_faces_mailer (gradle compileJava compileTestJava)..."
echo ""

chmod +x ./gradlew 2>/dev/null || true
./gradlew compileJava compileTestJava --no-daemon -q

echo ""
echo "✅ many_faces_mailer lint passed"
