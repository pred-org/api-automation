# File-by-file inventory (one to two lines each)

Short notes on what each file is for and how it fits the project (Java API automation: RestAssured, TestNG, Maven; Node sig-server for signing). Includes git-tracked paths and notable local-only files in the workspace.

## Documentation index (canonical)

| Topic | Document |
|-------|----------|
| Per-file purpose (this list) | `docs/FILE-BY-FILE-INVENTORY.md` |
| Test cases and coverage | `docs/TESTCASES-COVERED.md` |
| HTTP API surface | `docs/API_DOCUMENTATION.md` (canonical; replaces older API reference duplicates) |
| How to run tests and env | `docs/COMMANDS.md`, `README.md` |
| Objectives and state validation | `docs/OBJECTIVES_AND_ALIGNMENT.md` |
| Stack, architecture, design annex (enums, POJOs, utils, DB future) | `docs/FRAMEWORK.md` |
| Gaps and roadmap | `docs/GAPS-AND-ROADMAP.md` |

**Superseded (removed):** `API-REFERENCE.md`, `API_REFERENCE_FROM_LOAD_TESTS.md`, `APIS_IN_USE.md` (use **API_DOCUMENTATION.md**). `COMMANDS-AUTOMATION.md` (renamed to **COMMANDS.md**; covers Maven, TestNG, and `tools/`). `PHASE1_DESIGN.md` (subset superseded by **OBJECTIVES_AND_ALIGNMENT** and **GAPS-AND-ROADMAP**). `DESIGN_DISCUSSION_*.md` (merged into **FRAMEWORK.md** section 15). `HANDOFF_PLACE_ORDER_SIGNATURE_FIX.md` (merged into **PLACE_ORDER_SIGNATURE.md**). `PROJECT-FILE-INVENTORY.md` (index merged here). `flow.md` (diagram moved to **TESTCASES-COVERED.md** appendix). `TEST_CASES.md` / `TESTS_LIST.md` (use **TESTCASES-COVERED.md**).

**Optional artifacts (do not commit):** `test-output.txt`, k6 JSON/text outputs, `.env.session`, `.env.session2`.

---

## Root

| File | What it does / how |
|------|-------------------|
| `README.md` | Project overview, requirements, how to run `mvn test`, pointers to design docs. |
| `pom.xml` | Maven build: dependencies (RestAssured, TestNG, Allure, json-schema-validator), Surefire, TestNG suite path. |
| `.gitignore` | Ignores build output, secrets, session files, IDE noise. |
| `.env.template` | Example environment variable names for API URLs, keys, market IDs; no real secrets. |
| `start-sig-server.sh` | Shell helper to start the Node sig-server process used for login and order signatures. |
| `test-output.txt` | Captured Maven test log from a local run; not source of truth (can be regenerated). |
| `order-ids.json` | Optional at repo root; prefer `tools/scripts/output/` (gitignored) when using `place-only-to-file.js`. |
| `k6-failure-report.txt` | Artifact from a k6 run (gitignored). |
| `k6-place-cancel-summary.json` | Artifact from a k6 run (gitignored). |
| `random.txt` / `random2.txt` | Local scratch or notes; not part of the test framework. |
| `API_Automation_Playbook.docx` | Office doc playbook (human-readable process). |
| `API_Automation_Session.pptx` | Office slide deck for sessions. |

---

## `.cursor/rules`

| File | What it does / how |
|------|-------------------|
| `no-emoji.mdc` | Cursor rule: avoid emojis in code, docs, and commits for this repo. |
| `no-k6-rate-limit.mdc` | Cursor rule: do not suggest k6 or rate-limit tests unless the user asks for load/perf. |

---

## `docs`

| File | What it does / how |
|------|-------------------|
| `README.md` | Entry point for the `docs/` folder. |
| `DOCS-INDEX.md` | Map of all documentation files. |
| `API_DOCUMENTATION.md` | Canonical HTTP API reference (auth, orders, portfolio, deposits, sig-server) derived from service classes. |
| `COMMANDS.md` | Commands: Maven, TestNG, sig-server, `tools/k6`, `tools/scripts`, `tools/ops`. |
| `CONFIG_AND_TEST_DATA.md` | How config layers work: properties, env, `.env`, test data. |
| `ENABLE_TRADING_FLOW.md` | Safe-approval prepare/execute flow and signing. |
| `FRAMEWORK.md` | Stack, package layout, listeners, execution flow; section 15 design annex (enums, POJOs, utils, assertions, DB). |
| `GAPS-AND-ROADMAP.md` | What is covered vs gaps and possible next steps. |
| `NOTION_TASKS.md` | Task tracking text aligned with Notion or similar. |
| `OBJECTIVES_AND_ALIGNMENT.md` | Test objectives: state transitions, two users, success criteria. |
| `PLACE_ORDER_SIGNATURE.md` | EIP-712 place-order signing, backend alignment, and fix applied (UAT). |
| `POSTMAN_EIP712_ORDER_STRUCT.md` | EIP-712 order struct for manual Postman testing. |
| `TESTCASES-COVERED.md` | Authoritative list of test cases; appendix with suite run diagram (from former `flow.md`). |
| `WHAT_YOU_NEED_TO_START.md` | Prerequisites before running the suite (keys, sig-server, trading enabled). |
| `FILE-BY-FILE-INVENTORY.md` | This document: one-to-two-line purpose for each project file (tracked and common local paths). |

---

## `tools/k6`

| File | What it does / how |
|------|-------------------|
| `README.md` | How to run k6 scripts (load testing; optional). |
| `place-cancel-rate-limit.js` | k6 script for place/cancel patterns (load or rate scenarios). |
| `deposit-batch.js` | k6 batch deposit helper (see script header). |

---

## `tools/scripts`

| File | What it does / how |
|------|-------------------|
| `place-only-to-file.js` | Node script: places orders via sig-server and REST; writes order IDs under `tools/scripts/output/` by default. |
| `set_env.example.sh` | Example exports for shell env (copy to `set_env.sh`; `set_env.sh` is gitignored). |

---

## `tools/ops`

| File | What it does / how |
|------|-------------------|
| `run-verify-markets.sh` | Starts sig-server and market-smoke-server for ops verification. |
| `stop-verify-markets.sh` | Stops processes on ports 5050 and 5051. |
| `market-smoke-server/` | Node service for market verification (see its `README.md`). |

---


## `sig-server`

| File | What it does / how |
|------|-------------------|
| `package.json` | Node dependencies and npm scripts for the sig-server. |
| `package-lock.json` | Locked dependency tree for reproducible npm installs. |
| `multi-user-config.js` | Loads or maps multi-user keys/config for sig-server scripts. |
| `users-tokens.json` | Example or cached token data for local sig-server users (treat as sensitive). |
| `run-deposit-race.sh` | Shell script to exercise deposit race scenarios against APIs. |
| `api/create-api-key.js` | Node HTTP client: POST internal create-api-key endpoint, parse key from body. |
| `api/create-api-key-client.js` | Alternate or thin client for create-api-key (shared with login tooling). |
| `api/get-access-token.js` | Script: login flow to print access token and export-friendly lines. |
| `api/get-new-access-tokens.js` | Refresh or fetch new tokens for one or more users. |
| `api/place-order.js` | Standalone place-order via sig-server signing and public API. |
| `api/place-order-from-wallet.js` | Place order variant that signs from wallet config. |
| `api/verify-setup.js` | Validates `config.js` / env (private key, EOA) before other scripts. |
| `execution/enable-trading.js` | Node script driving enable-trading prepare/execute with signatures. |
| `scripts/login-fresh-user.js` | End-to-end login for a fresh user via sig-server. |
| `scripts/print-order-signature.js` | Debug helper to print a signed order payload. |
| `scripts/sign-login-create-proxy.js` | Produces create-proxy signature for login body. |
| `signatures/server.js` | Express (or similar) HTTP server exposing `/sign-create-proxy`, `/sign-order`, `/sign-safe-approval`. |
| `signatures/login.js` | Login-related signing helpers used by the server or scripts. |
| `signatures/sign-tx-hash-for-execute.js` | Signs transaction hash for enable-trading execute step. |
| `signatures/verify-order-hash.js` | Verifies order hash vs signature for debugging. |

---

## `src/main/java/com/pred/apitests`

### `base`

| File | What it does / how |
|------|-------------------|
| `BaseService.java` | RestAssured wrapper: `given` with JSON, auth+cookie headers, GET/POST/PUT/DELETE, timeouts, shared filters. |

### `config`

| File | What it does / how |
|------|-------------------|
| `Config.java` | Loads classpath properties, `.env` from project root, and system properties; exposes API base URIs, keys, market/token IDs, sig-server URL. |

### `filters`

| File | What it does / how |
|------|-------------------|
| `LoggingFilter.java` | RestAssured filter that logs request/response for debugging test HTTP traffic. |

### `listeners`

| File | What it does / how |
|------|-------------------|
| `TestListener.java` | TestNG listener: logs test start/pass/skip/fail for the suite. |
| `SlackNotificationListener.java` | TestNG listener: posts a short summary (pass/fail/skip counts) to Slack when configured. |

### `model/request`

| File | What it does / how |
|------|-------------------|
| `LoginRequest.java` | POJO + builder for login-with-signature JSON (`data.wallet_address`, `signature`, etc.). |
| `PlaceOrderRequest.java` | POJO + builder for place-order API body (side, price, quantity, amount, signature, `reduce_only`, etc.). |
| `SignOrderRequest.java` | POJO + builder for sig-server `/sign-order` body (questionId, intent, maker, signer, etc.). |
| `DepositRequest.java` | Internal deposit body: `user_id`, `amount`. |
| `CashflowDepositRequest.java` | Public cashflow deposit: `salt`, `transaction_hash`, `timestamp`. |

### `model/response`

| File | What it does / how |
|------|-------------------|
| `LoginResponse.java` | Maps login JSON: `access_token`, nested `data` with user id and proxy wallet. |
| `BalanceResponse.java` | Optional typed balance response (if used by mappers). |
| `PrepareResponse.java` | Enable-trading prepare response shape for execute step. |
| `SignCreateProxyResponse.java` | Sig-server response for create-proxy (signature, wallet address). |
| `SignOrderResponse.java` | Sig-server response for sign-order (`ok`, `signature`, optional fields). |
| `SignSafeApprovalResponse.java` | Sig-server response for safe-approval hash signing. |

### `service`

| File | What it does / how |
|------|-------------------|
| `AuthService.java` | Calls login, refresh token, create API key; parses tokens; integrates with `TokenManager` and session refresh. |
| `OrderService.java` | `placeOrder`, `cancelOrder`, `getOrderbook` against public API paths with auth and wallet headers. |
| `PortfolioService.java` | Balance, positions, open orders, trade history, PnL, earnings, optional internal balance. |
| `SignatureService.java` | HTTP client to sig-server: sign-create-proxy, sign-order, sign-safe-approval. |
| `EnableTradingService.java` | POST safe-approval prepare and execute with JSON bodies. |
| `DepositService.java` | Internal deposit then cashflow deposit using `DepositRequest` / `CashflowDepositRequest`. |
| `UserService.java` | GET `/user/me` and maintenance flag for health checks. |
| `MarketDiscoveryService.java` | GET leagues and fixtures (market discovery) on public base URI. |

### `util`

| File | What it does / how |
|------|-------------------|
| `TokenManager.java` | Singleton holding user1 access token, refresh cookie, user id, proxy, EOA, private key; expiry helpers. |
| `UserSession.java` | Immutable-ish session bag for any user (token, cookie, ids, keys). |
| `SecondUserContext.java` | Loads user2 session from `.env.session2` or `USER_2_*` env vars. |
| `SessionFileWriter.java` | Writes `.env.session` / `.env.session2` after successful login for reuse. |

---

## `src/test/java/com/pred/apitests`

| File | What it does / how |
|------|-------------------|
| `FrameworkSmokeTest.java` | Single test: `Config.getPublicBaseUri()` is non-blank (no HTTP). |
| `HealthCheckTest.java` | Calls maintenance and user `me` endpoints when token exists. |
| `LoginTest.java` | API key creation and negative login body tests. |

### `base`

| File | What it does / how |
|------|-------------------|
| `BaseApiTest.java` | Test base: `getSession()`, token refresh hook, shared helpers for balance/trade-history parsing, user2 refresh. |

### `test`

| File | What it does / how |
|------|-------------------|
| `AuthFlowTest.java` | `@BeforeSuite` login via sig-server; stores session; tests refresh, invalid signature, token print. |
| `AuthFlowTestUser2.java` | Logs in user2, writes `.env.session2`, validates second user load. |
| `EnableTradingTest.java` | User1 enable-trading prepare/execute with signatures. |
| `EnableTradingTestUser2.java` | Same for user2 (`SecondUserContext`). |
| `DepositTest.java` | Internal + cashflow deposit flow for user1. |
| `DepositTestUser2.java` | Deposit tests using second user session. |
| `PortfolioTest.java` | Contract and status tests for balance, positions, earnings, trade history, invalid token. |
| `OrderTest.java` | Place order happy path, short side, validation negatives, cancel invalid id; JSON schema checks. |
| `OrderTestUser2.java` | Same scenarios as `OrderTest` with `SecondUserContext`. |
| `OrderbookTest.java` | Orderbook structure and bid/ask behavior around a short order. |
| `MarketDiscoveryTest.java` | Leagues and fixtures endpoints. |
| `CancelOrderTest.java` | Cancel valid/invalid orders, trade history and balance restoration after cancel. |
| `CancelOrderTestUser2.java` | Cancel tests for user2. |
| `OrderFlowTest.java` | Multi-step flows: two orders with cancel, two-user match, matched short, unrealized PnL, long/short match assertions. |
| `ClosePositionTest.java` | Open LONG with spread prices, close with reduceOnly SHORT and counterparty liquidity; balance and PnL checks. |
| `BalanceServiceTest.java` | Balance changes around place/cancel and reserved vs available logic. |
| `BalanceIntegrityTest.java` | Stricter balance delta and insufficient-balance scenarios. |

### `util` (test)

| File | What it does / how |
|------|-------------------|
| `TestPreConditions.java` | Helpers to query positions/balance for gating or diagnostics. |
| `PollingUtil.java` | Generic poll-until-timeout utilities for async backend state. |
| `SchemaValidator.java` | Asserts RestAssured responses match JSON Schema files under `src/test/resources/schemas`. |

---

## `src/test/resources`

| File | What it does / how |
|------|-------------------|
| `suite.xml` | TestNG suite: ordered classes and groups for full regression. |
| `testdata.properties` | Default test URLs and ids (overridable by env). |
| `application.properties` | Optional classpath overrides for tests. |
| `allure.properties` | Allure report configuration for Surefire. |
| `logback-test.xml` | Logback config for test runs (levels, appenders). |
| `schemas/balance-response.json` | JSON Schema for balance API responses used in assertions. |
| `schemas/cancel-order-response.json` | JSON Schema for cancel order success body. |
| `schemas/earnings-response.json` | JSON Schema for earnings endpoint. |
| `schemas/open-orders-response.json` | JSON Schema for open orders list. |
| `schemas/orderbook-response.json` | JSON Schema for orderbook payload. |
| `schemas/place-order-response.json` | JSON Schema for place order 202 response. |
| `schemas/positions-response.json` | JSON Schema for positions list. |
| `schemas/trade-history-response.json` | JSON Schema for trade history list. |

---

## Maintenance

- Regenerate or trim this file when large files are added or removed; prefer `git ls-files` plus `git status -u` for accuracy.
- Binary artifacts (`*.docx`, `*.pptx`) and local scratch files may not be tracked; descriptions above still apply if present in the workspace.
