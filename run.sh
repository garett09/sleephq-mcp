#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

if [ -f .env ]; then
  # shellcheck disable=SC1091
  set -a
  source .env
  set +a
fi

echo "Building…" >&2
./mvnw clean package -DskipTests

exec java -jar target/sleephq-mcp-0.0.1-SNAPSHOT.jar "$@"
