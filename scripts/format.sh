#!/usr/bin/env bash
# Reformat Java / Gradle Kotlin DSL sources to tab indentation.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
python3 "$ROOT/scripts/format-java.py"
echo "✅ many_faces_mailer format complete"
