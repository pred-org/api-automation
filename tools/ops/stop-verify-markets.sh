#!/usr/bin/env bash
# Run from anywhere: bash tools/ops/stop-verify-markets.sh
lsof -ti:5050 | xargs kill -9 2>/dev/null || true
lsof -ti:5051 | xargs kill -9 2>/dev/null || true
echo "Both servers stopped."
