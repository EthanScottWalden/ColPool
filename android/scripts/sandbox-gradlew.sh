#!/bin/bash
# Runs ./gradlew inside the Claude Code agent sandbox. Humans use plain ./gradlew.
#
# Gradle's JVMs ignore the sandbox's proxy environment variables, so this parses
# $HTTPS_PROXY at runtime and passes it only as -D flags on this command line.
# Nothing is written to disk and nothing is echoed. When HTTPS_PROXY is unset it
# just runs ./gradlew. Usage: android/scripts/sandbox-gradlew.sh assembleDebug
#
# Always --no-daemon. Each sandboxed command runs in its own process namespace with
# its own short-lived proxy credentials. A daemon left over from an earlier command
# keeps holding the Gradle cache lock, which `--stop` can't reach, and it would keep
# the earlier command's proxy credentials.
set -euo pipefail
cd "$(dirname "$0")/.."

export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
tmp="${TMPDIR:-/tmp}"
# /tmp is read-only in the sandbox; the tunneling flag lets Java send proxy Basic auth over HTTPS.
jvm="-Djdk.http.auth.tunneling.disabledSchemes= -Djava.io.tmpdir=$tmp"

if [[ -n "${HTTPS_PROXY:-}" ]]; then
  re='^https?://([^:@]+):([^@]*)@([^:/]+):([0-9]+)'
  if [[ "$HTTPS_PROXY" =~ $re ]]; then
    for s in http https; do
      jvm+=" -D$s.proxyHost=${BASH_REMATCH[3]} -D$s.proxyPort=${BASH_REMATCH[4]}"
      jvm+=" -D$s.proxyUser=${BASH_REMATCH[1]} -D$s.proxyPassword=${BASH_REMATCH[2]}"
    done
    jvm+=" -Dhttp.nonProxyHosts=localhost|127.0.0.1"
  fi
fi
export GRADLE_OPTS="${GRADLE_OPTS:-} $jvm"
exec ./gradlew --no-daemon "-Dorg.gradle.jvmargs=-Xmx3g -Dfile.encoding=UTF-8 $jvm" "$@"
