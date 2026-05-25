#!/usr/bin/env bash
# Clean rebuild + single-instance MCP server (avoids stale JVM / NoClassDefFoundError).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

readonly ARTIFACT_JAR="sleephq-mcp-0.0.1-SNAPSHOT.jar"
readonly BUILD_JAR="target/${ARTIFACT_JAR}"
readonly DIST_JAR="dist/${ARTIFACT_JAR}"
readonly LOCK_DIR="${SCRIPT_DIR}/.sleephq-mcp.run.lock.d"
readonly PORT="${SLEEPHQ_MCP_PORT:-8080}"
readonly STOP_TIMEOUT_SEC=15

if [ -f .env ]; then
  # shellcheck disable=SC1091
  set -a
  source .env
  set +a
fi

release_build_lock() {
  rm -rf "${LOCK_DIR}"
}

# Lock only covers the build phase. exec java replaces this shell, so we must release before exec
# (otherwise .sleephq-mcp.run.lock.d is left behind forever).
is_build_lock_held() {
  if [ ! -d "${LOCK_DIR}" ]; then
    return 1
  fi
  if [ ! -f "${LOCK_DIR}/pid" ]; then
    return 1
  fi
  local holder_pid
  holder_pid="$(cat "${LOCK_DIR}/pid" 2>/dev/null || true)"
  if [ -z "${holder_pid}" ]; then
    return 1
  fi
  kill -0 "${holder_pid}" 2>/dev/null
}

acquire_build_lock() {
  if is_build_lock_held; then
    echo "error: another run.sh is building (lock: ${LOCK_DIR}, pid $(cat "${LOCK_DIR}/pid"))" >&2
    exit 1
  fi
  if [ -d "${LOCK_DIR}" ]; then
    echo "Removing stale build lock (previous run.sh exited without cleanup)…" >&2
    release_build_lock
  fi
  mkdir "${LOCK_DIR}"
  echo "$$" > "${LOCK_DIR}/pid"
}

trap 'release_build_lock' EXIT INT TERM

acquire_build_lock

stop_existing_server() {
  echo "Stopping any existing sleephq-mcp on port ${PORT}…" >&2

  if command -v pgrep >/dev/null 2>&1; then
    local pids
    pids="$(pgrep -f "${ARTIFACT_JAR}" 2>/dev/null || true)"
    if [ -n "${pids}" ]; then
      echo "  SIGTERM PIDs: ${pids}" >&2
      # shellcheck disable=SC2086
      kill ${pids} 2>/dev/null || true
    fi
  fi

  if command -v lsof >/dev/null 2>&1; then
    local i=0
    while [ "${i}" -lt "${STOP_TIMEOUT_SEC}" ]; do
      local listen_pids
      listen_pids="$(lsof -ti "tcp:${PORT}" -sTCP:LISTEN 2>/dev/null || true)"
      if [ -z "${listen_pids}" ]; then
        return 0
      fi
      if [ "${i}" -eq 0 ]; then
        echo "  SIGTERM port ${PORT} PIDs: ${listen_pids}" >&2
      fi
      # shellcheck disable=SC2086
      kill ${listen_pids} 2>/dev/null || true
      sleep 1
      i=$((i + 1))
    done

    listen_pids="$(lsof -ti "tcp:${PORT}" -sTCP:LISTEN 2>/dev/null || true)"
    if [ -n "${listen_pids}" ]; then
      echo "  SIGKILL port ${PORT} PIDs: ${listen_pids}" >&2
      # shellcheck disable=SC2086
      kill -9 ${listen_pids} 2>/dev/null || true
      sleep 1
    fi
  fi

  if command -v lsof >/dev/null 2>&1; then
    if lsof -ti "tcp:${PORT}" -sTCP:LISTEN >/dev/null 2>&1; then
      echo "error: port ${PORT} still in use — stop the old server manually, then re-run ./run.sh" >&2
      exit 1
    fi
  fi
}

verify_jar_contents() {
  local jar_path="$1"
  local missing=0
  for required in \
    "BOOT-INF/classes/com/adriangarett/sleephqmcp/support/TeamFileResolver.class" \
    "BOOT-INF/classes/com/adriangarett/sleephqmcp/support/BinaryDownloadSupport.class" \
    "BOOT-INF/classes/com/adriangarett/sleephqmcp/support/O2ImportResolver.class" \
    "BOOT-INF/lib/spring-webmvc"; do
    if ! jar tf "${jar_path}" | grep -q "${required}"; then
      echo "error: ${jar_path} missing ${required}" >&2
      missing=1
    fi
  done
  if [ "${missing}" -ne 0 ]; then
    exit 1
  fi
}

select_java() {
  if [ -n "${JAVA_HOME:-}" ] && [ -x "${JAVA_HOME}/bin/java" ]; then
    echo "${JAVA_HOME}/bin/java"
    return
  fi
  if command -v /usr/libexec/java_home >/dev/null 2>&1; then
    local j21
    j21="$(/usr/libexec/java_home -v 21 2>/dev/null || true)"
    if [ -n "${j21}" ] && [ -x "${j21}/bin/java" ]; then
      echo "${j21}/bin/java"
      return
    fi
  fi
  command -v java
}

# 1) Stop old JVM before deleting target/ (prevents inode/classloader corruption).
stop_existing_server

echo "Removing previous build output (target/)…" >&2
rm -rf target

echo "Building (clean package)…" >&2
./mvnw clean package -DskipTests \
  -U \
  -Dmaven.build.cache.enabled=false \
  -Dmaven.compiler.useIncrementalCompilation=false \
  --no-transfer-progress

if [ ! -f "${BUILD_JAR}" ]; then
  echo "error: expected jar not found: ${BUILD_JAR}" >&2
  exit 1
fi

verify_jar_contents "${BUILD_JAR}"

mkdir -p dist
echo "Publishing jar to ${DIST_JAR}…" >&2
cp -f "${BUILD_JAR}" "${DIST_JAR}"

JAVA_BIN="$(select_java)"
echo "Java: $("${JAVA_BIN}" -version 2>&1 | head -1)" >&2
echo "Starting ${DIST_JAR} on port ${PORT} (absolute: ${SCRIPT_DIR}/${DIST_JAR})…" >&2

# Run from dist/ so a later target/ wipe cannot affect this process's jar path.
cd "${SCRIPT_DIR}"
release_build_lock
trap - EXIT INT TERM
exec "${JAVA_BIN}" -jar "${SCRIPT_DIR}/${DIST_JAR}" "$@"
