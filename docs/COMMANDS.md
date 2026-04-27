# Commands and tooling reference

Run shell commands from the **repository root** (directory that contains `pom.xml` and `sig-server/`).

**Prerequisites:** Java 17+, Maven 3.6+.

---

## Sig-server

Sig-server provides `sign-create-proxy` (login) and `sign-order` for tests and scripts. Default URL: `http://localhost:5050`.

| How | Command / note |
|-----|------------------|
| **During `mvn test`** | The build runs an `exec-maven-plugin` step after `process-test-classes` that starts `sig-server/signatures/server.js` and waits for port 5050, then stops it after tests. You do not need a second terminal for the default suite. |
| **Manual (separate terminal)** | `./start-sig-server.sh` or `bash start-sig-server.sh` or `cd sig-server && node signatures/server.js` |
| **Permission denied** | Run once: `chmod +x start-sig-server.sh` |

---

## Prereqs and config

| Item | Description |
|------|-------------|
| **Sig-server** | Required for login and order signing when running tests that use signatures. |
| **`.env`** | Optional, project root. Loaded by `Config` (e.g. `API_BASE_URI_PUBLIC`, `PRIVATE_KEY`, `MARKET_ID`). |
| **`testdata.properties`** | `src/test/resources/testdata.properties` — base URLs, market ID, token ID, wallet fields. Do not commit real secrets. |
| **API key** | Leave `api.key` unset to create a new key per run, or set `API_KEY` / `api.key` to reuse. |

---

## Auth (user 1 and user 2)

| Command | Description |
|---------|-------------|
| `mvn test -Dtest=AuthFlowTest` | Login as **user 1**. Writes `.env.session`. Run first for other tests that need TokenManager. |
| `mvn test -Dtest=AuthFlowTestUser2` | Login as **user 2**. Writes `.env.session2`. Set `PRIVATE_KEY_2` or `second.user.private.key`. |

**In-suite refresh:** The full suite refreshes user 1 and user 2 when tokens are expiring soon (or on schedule for user 2). Re-run auth tests after long idle if needed.

---

## Run tests (Maven / TestNG)

| Command | Description |
|---------|-------------|
| `mvn test` | Default suite in `src/test/resources/suite.xml`. |
| `mvn test -Dsuite=src/test/resources/suite.xml` | Same suite, explicit path. |
| `mvn test -Dtest=OrderTest` | Single class. |
| `mvn test -Dtest=OrderTest#flow_twoUsers_placeLongAndShort_mayMatch_positions` | Single method (needs user 2 session). |
| `mvn test -Dtest=PortfolioTest` | Portfolio only. |
| `mvn test -Dtest=AuthFlowTest,AuthFlowTestUser2` | Both auth flows. |
| `mvn test -Dtest=NewUserLoginDepositTest -DforkCount=0` | Standalone: new user login + deposit (hardcoded test user; not in default suite). Sig-server must be reachable. |

**Allure:** `mvn allure:serve` after a run.

**Override base URI:** `mvn test -Dapi.base.uri=https://your-host` or `export API_BASE_URI_PUBLIC=...`.

**Capture output:** `mvn clean test > test-output.txt 2>&1`

---

## Default suite (summary)

| Area | Typical classes |
|------|-----------------|
| Smoke / login | FrameworkSmokeTest, LoginTest |
| Auth + health | AuthFlowTest, HealthCheckTest |
| Enable trading | EnableTradingTest, EnableTradingTestUser2 |
| Auth user 2 | AuthFlowTestUser2 |
| Deposit / portfolio | DepositTest, PortfolioTest |
| Orders / book / cancel | OrderTest, OrderTestUser2, OrderbookTest, CancelOrderTest, CancelOrderTestUser2 |
| Flow / balance | OrderFlowTest, BalanceServiceTest, BalanceIntegrityTest |

**User 2 / 401 / enable-trading troubleshooting:** See notes in [docs/FRAMEWORK.md](FRAMEWORK.md) and failure output under `target/surefire-reports/`.

---

## Two-user flow (quick)

1. `mvn test -Dtest=AuthFlowTest`  
2. Set second wallet; `mvn test -Dtest=AuthFlowTestUser2`  
3. `mvn test -Dtest=OrderTest#flow_twoUsers_placeLongAndShort_mayMatch_positions`  
4. Optional: `mvn test` for full suite  

Session files: `.env.session` (user 1), `.env.session2` (user 2).

---

## Config properties (summary)

| Property / env | Purpose |
|----------------|---------|
| `api.base.uri.public` / `API_BASE_URI_PUBLIC` | Public API base |
| `api.base.uri.internal` / `API_BASE_URI_INTERNAL` | Internal API base |
| `market.id` / `MARKET_ID` | Market ID |
| `token.id` / `TOKEN_ID` | Token ID for place order |
| `private.key` / `PRIVATE_KEY` | User 1 private key |
| `eoa.address` / `EOA_ADDRESS` | User 1 EOA (optional) |
| `sig.server.url` / `SIG_SERVER_URL` | Sig-server URL |
| `second.user.private.key` / `PRIVATE_KEY_2` | User 2 private key |
| `second.user.eoa` / `EOA_ADDRESS_2` | User 2 EOA |
| `second.user.api.key` / `API_KEY_2` | User 2 API key if required |

---

## Reports and artifacts

| Item | Location / note |
|------|-----------------|
| Surefire | `target/surefire-reports/` |
| Allure | `mvn allure:serve` |
| Session env | `.env.session`, `.env.session2` (gitignored) |

---

## Repository tools (non-Java)

Paths are under `tools/`. Run k6 and Node commands from the **repo root** so paths resolve correctly.

### `tools/k6/` (load / performance scripts)

| Command | Purpose |
|---------|---------|
| `k6 run tools/k6/deposit-batch.js -e USER_ID=<uuid>` | Deposit batch helper (see script header for env vars). |
| `k6 run tools/k6/place-cancel-rate-limit.js` | Place/cancel load script (see `tools/k6/README.md` and file header). |

### `tools/scripts/` (utilities)

| Command | Purpose |
|---------|---------|
| `node tools/scripts/place-only-to-file.js` | Places orders and writes order IDs (default file: `tools/scripts/output/order-ids.json`). Requires session env (e.g. `source .env.session`). |

| Copy env template | `cp tools/scripts/set_env.example.sh tools/scripts/set_env.sh` then edit; `source tools/scripts/set_env.sh` (local only; `set_env.sh` is gitignored). |

### `tools/ops/` (market verification)

| Command | Purpose |
|---------|---------|
| `bash tools/ops/run-verify-markets.sh` | Starts sig-server and `tools/ops/market-smoke-server` (see script for ports). |
| `bash tools/ops/stop-verify-markets.sh` | Stops listeners on ports 5050 and 5051. |

Details: `tools/ops/market-smoke-server/README.md`.

---

## See also

- [DOCS-INDEX.md](DOCS-INDEX.md) — documentation map  
- [WHAT_YOU_NEED_TO_START.md](WHAT_YOU_NEED_TO_START.md) — setup checklist  
- [PROJECT-STRUCTURE.md](../PROJECT-STRUCTURE.md) — repo layout  
