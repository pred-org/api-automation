# Project Structure

This repo is intentionally split into these main areas:

- `docs/`
  - Versioned technical documentation (commands, framework, API reference). See `docs/DOCS-INDEX.md`.
- `src/`
  - Java API automation framework and TestNG suites (primary artifact with `pom.xml`).
- `sig-server/`
  - Node signing server used by Java tests and tooling (stays at repo root).
- `tools/k6/`
  - k6 load and batch scripts (`deposit-batch.js`, `place-cancel-rate-limit.js`, `README.md`).
- `tools/scripts/`
  - Standalone utility scripts (`place-only-to-file.js`, `set_env.example.sh`; local `set_env.sh` is gitignored).
- `tools/ops/`
  - Ops tooling: `market-smoke-server/`, `run-verify-markets.sh`, `stop-verify-markets.sh`.

## Root-level convenience scripts

- `start-sig-server.sh`
  - Optional helper to start sig-server only.

## Environment files

- `.env` (default)
- `.env.<env>` (optional, e.g. `.env.testnet`)
- `.env.session`, `.env.session2` (generated local sessions)

## Local `documents/` folder

- The root `documents/` directory is **gitignored** (private notes, exports, HTML, Office files). It is not part of the build.
- Prefer `docs/` for anything that should ship with the repo.

## Notes for safe maintenance

- Do not move `sig-server/signatures/server.js` or `tools/ops/market-smoke-server/server.js` without updating scripts and docs.
- Keep generated runtime artifacts out of root (reports, order ID dumps, k6 summaries).
