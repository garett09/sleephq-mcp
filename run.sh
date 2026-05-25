#!/usr/bin/env bash
# Always run from a fresh build: wipe target/, disable Maven/compiler caches, refresh deps.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

if [ -f .env ]; then
  # shellcheck disable=SC1091
  set -a
  source .env
  set +a
fi

readonly ARTIFACT_JAR="sleephq-mcp-0.0.1-SNAPSHOT.jar"
readonly JAR_PATH="target/${ARTIFACT_JAR}"

echo "Removing previous build output (target/)…" >&2
rm -rf target

echo "Building (clean package, no cache, dependency refresh)…" >&2
./mvnw clean package -DskipTests \
  -U \
  -Dmaven.build.cache.enabled=false \
  -Dmaven.compiler.useIncrementalCompilation=false \
  --no-transfer-progress

if [ ! -f "$JAR_PATH" ]; then
  echo "error: expected jar not found: ${JAR_PATH}" >&2
  exit 1
fi

echo "Starting ${JAR_PATH}…" >&2
exec java -jar "$JAR_PATH" "$@"
