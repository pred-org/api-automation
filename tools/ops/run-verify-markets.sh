#!/usr/bin/env bash
# Run from anywhere: bash tools/ops/run-verify-markets.sh
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SIG_PORT=5050
SMOKE_PORT=5051
SIG_PID=""

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Error: required command '$1' not found. Please install it and re-run."
    exit 1
  fi
}

cleanup() {
  if [[ -n "${SIG_PID}" ]] && kill -0 "${SIG_PID}" >/dev/null 2>&1; then
    kill "${SIG_PID}" >/dev/null 2>&1 || true
  fi
  lsof -ti:"${SIG_PORT}" | xargs kill -9 2>/dev/null || true
}

trap cleanup SIGINT SIGTERM EXIT

require_cmd node
require_cmd npm
require_cmd curl
require_cmd lsof

echo "========================================"
echo " PRED Market Verification Server"
echo "========================================"

if [[ ! -f "${PROJECT_ROOT}/.env" ]]; then
  echo "Warning: ${PROJECT_ROOT}/.env not found."
  echo "sig-server reads project root .env for wallet config."
fi
SMOKE_DIR="${PROJECT_ROOT}/tools/ops/market-smoke-server"
if [[ ! -f "${SMOKE_DIR}/.env" ]]; then
  echo "Warning: ${SMOKE_DIR}/.env not found."
  echo "You can copy from tools/ops/market-smoke-server/.env.example"
fi

echo "Cleaning up old processes on ports ${SIG_PORT} and ${SMOKE_PORT}..."
lsof -ti:"${SIG_PORT}" | xargs kill -9 2>/dev/null || true
lsof -ti:"${SMOKE_PORT}" | xargs kill -9 2>/dev/null || true

echo "Starting sig-server on port ${SIG_PORT}..."
(
  cd "${PROJECT_ROOT}/sig-server"
  npm install
  nohup node signatures/server.js >/tmp/sig-server.log 2>&1 &
  echo $! > /tmp/pred-sig-server.pid
)
SIG_PID="$(cat /tmp/pred-sig-server.pid 2>/dev/null || true)"

echo "Waiting for sig-server health on port ${SIG_PORT}..."
for i in $(seq 1 30); do
  if curl -fsS "http://127.0.0.1:${SIG_PORT}/" >/dev/null 2>&1; then
    break
  fi
  if [[ "${i}" -eq 30 ]]; then
    echo "Error: sig-server failed to start on port ${SIG_PORT} within 30s."
    echo "Check logs: /tmp/sig-server.log"
    exit 1
  fi
  sleep 1
done

echo "Starting market-smoke-server on port ${SMOKE_PORT}..."
echo "========================================"
echo "Starting sig-server on port 5050..."
echo "Starting market-smoke-server on port 5051..."
echo
echo "Once running, send POST request:"
echo "  curl -X POST http://localhost:5051/verify-markets \\"
echo "    -H \"Content-Type: application/json\" \\"
echo "    -d '{\"canonicalName\":\"<fixture-name>\",\"privateKey1\":\"0x...\",\"privateKey2\":\"0x...\"}'"
echo
echo "Health check: curl http://localhost:5051/health"
echo "Press Ctrl+C to stop both servers."
echo "========================================"

cd "${SMOKE_DIR}"
npm install
node server.js
