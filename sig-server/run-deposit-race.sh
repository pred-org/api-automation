#!/usr/bin/env bash
# Wrapper: run deposit-race from project root (so this works when you're in sig-server).
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec "$SCRIPT_DIR/../deposit/run-deposit-race.sh" "$@"
