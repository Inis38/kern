#!/bin/bash
set -euo pipefail

if [[ -z "${JAVA_HOME:-}" ]]; then
  if command -v /usr/libexec/java_home >/dev/null 2>&1; then
    JAVA_HOME="$(/usr/libexec/java_home -v 21 2>/dev/null || true)"
    export JAVA_HOME
  fi
fi

if [[ -z "${JAVA_HOME:-}" ]]; then
  echo "JAVA_HOME is not set and JDK 21 was not found. Install JDK 21 or export JAVA_HOME." >&2
  exit 1
fi

exec ./gradlew "$@"
