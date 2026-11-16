#!/bin/bash
# Runs npm inside the Claude Code agent sandbox, where ~/.npm is read-only.
# Uses a project-local cache (.cache/npm, git-ignored). Humans use plain npm.
# Usage: functions/scripts/sandbox-npm.sh ci
set -euo pipefail
cd "$(dirname "$0")/.."
export npm_config_cache="$(cd .. && pwd)/.cache/npm"
exec npm "$@"
