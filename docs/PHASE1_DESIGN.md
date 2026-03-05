# Phase 1: Single User Trading Flow — Design & Scope

## 1. Scope

### In scope
- **Backend API validation only** (no UI)
- **Single user**: one user logs in; we get proxy wallet, privy wallet, access token, user id
- **Deposit** to that user (so they have balance to trade)
- **Single market**: market ID from **config** (you change it as needed; no dynamic discovery for now)
- **Place order / open position** in that market (normal user behaviour, not market maker)
- **You close and resolve** the market (manual or via API); automation then verifies
- **Settlement**: if user had long position on winning outcome → $1 per share; balance updates
- **Verification**: trade checks, position checks, balance checks — all validated

### Out of scope
- User interface behavior
- General deposits/withdrawals UX (deposit in this flow is only to fund the test user)
- Market maker or liquidity bot behavior
- Performance or load testing

*On-chain transaction hash and balance reconciliation → separate phase (ledger integrity).*

**Reference:** The **pred-load-tests** project (sibling repo) contains the same flow and API details: login, enable-trading, deposit, place-order. API endpoints, request/response shapes, and base URLs are summarized in **[API_REFERENCE_FROM_LOAD_TESTS.md](API_REFERENCE_FROM_LOAD_TESTS.md)**. Use that doc and pred-load-tests scripts as the source when implementing in RestAssured.

---

## 2. Services involved

| Service | Role |
|--------|------|
| **auth-service** | User authentication and identity verification |
| **market-discovery** | Market metadata and routing |
| **order-service** (per market) | Order placement and matching |
| **position-hq** | Source of truth for user positions |
| **balance-service** | Source of truth for user balances |
| **settlement / contract-interaction** | Market resolution and redemption |

---

## 3. Automation flow

| Step | Name | What we do |
|------|------|------------|
| **1** | Login | One user authenticates → get **access token**, **user id**, **proxy wallet**, **privy wallet** |
| **2** | Deposit | Deposit amount to that user id (so user has balance to trade) |
| **3** | Market | Use **market ID from config** (you change as needed); no discovery for now |
| **4** | Place order / open position | Place order or open position in that market (normal user, not market maker) |
| **5** | You resolve | You close and resolve the market (manual or API); automation waits/polls until resolved |
| **6** | Settlement | If user had long on winning outcome → $1 per share; balance updates automatically |
| **7** | Verify | Trade checks, position checks, balance checks — all verified |

**Data flow:** Credentials from env/config → Step 1 (token, user id, wallets). Step 2 uses user id. Market ID from config. Token used for all authenticated calls. **Goal:** everything verified in trade and balance checks.

---

## 4. Key validation criteria

- **Position correctness** — Position in position-hq matches order (market, side, size) and updates correctly.
- **Balance accuracy** — Balance after settlement matches expected (initial ± PnL).
- **No duplicate settlement** — Each position settled once; no double credit/debit.
- **No negative balance** — Final balance ≥ 0 (or as per business rules).

---

## 5. Success criteria

- Automation is **reliable** and **deterministic**.
- Suitable for **CI execution** (config via env/properties; no manual steps).
- Single run = one full lifecycle; no stress/load.

---

## 6. Future considerations

- Additional edge cases
- Multi-user scenarios
- Performance testing  
*(To be added in later phases.)*

---

## 7. APIs we need

| # | Purpose | What we need from response / config |
|---|---------|-------------------------------------|
| 1 | **Login** | Access token, user id, proxy wallet, privy wallet (credentials from env/properties) |
| 2 | **Deposit** | Credit the user id with an amount (amount from config) |
| 3 | **Market** | Market ID from **config** (you set per run) |
| 4 | **Place order / open position** | Order/position in that market; we keep order/position id if returned |
| 5 | **Get positions** | Verify position exists; after resolve, verify settled/redeemed |
| 6 | **Market status** | Poll until market resolved (or you trigger resolve; we just verify) |
| 7 | **Get balance** | Before/after balance; assert updated correctly (e.g. long winning = +$1 per share), no negative balance |

---

## 8. Implementation checklist (when ready)

- [ ] API contract per service (paths, methods, request/response shapes)
- [ ] Login: credentials from env/properties → store token, user id, proxy wallet, privy wallet
- [ ] Deposit: call deposit API for that user id; amount from config
- [ ] Market ID from config (e.g. application.properties or env)
- [ ] Place order / open position: use token + market ID; order params from config
- [ ] Position check: call position-hq; assert position exists for market
- [ ] After you resolve: poll market status until resolved; poll positions until redeemed
- [ ] Balance: get balance; assert correctness (e.g. +$1 per share for winning long), no duplicate settlement, no negative balance

*Phase 1 validates internal trading and settlement correctness (normal user behaviour, not market maker).*

---

## 9. Test stack: POJO / enum / assertions / framework / reporting

**Framework:** **JUnit 5** (junit-jupiter). The project does **not** use TestNG. Tests use `@Test`, `@BeforeAll`, `@DisplayName`, etc.

**Assertions:**
- **AssertJ** (`assertThat(...).isNotBlank()`, `.isEqualTo()`, etc.) for flexible, readable assertions on parsed values (e.g. token present, balance >= 0).
- **RestAssured** `.then().statusCode(200)`, `.body("field", equalTo(...))` for status and JSON path checks directly on the response.
Use both as needed: RestAssured for status and simple body checks; AssertJ for logic on extracted values.

**POJO (when we implement):**
- **Response DTOs** (optional): e.g. `LoginResponse` (accessToken, userId, proxyWalletAddress), `BalanceResponse` (success, usdcBalance, positionBalance), `PositionsResponse` or a list of position items. Use when we want type-safe parsing and AssertJ on fields; otherwise RestAssured `response.jsonPath().getString("data.user_id")` is enough.
- **Request DTOs** (optional): e.g. for login body, deposit body, place-order body. Help keep payloads consistent and avoid string typos; not required if we build JSON with maps or inline strings.

**Enum (when we implement):**
- Use where fixed values are used in requests or assertions: e.g. `OrderSide` (LONG, SHORT), `OrderType` (LIMIT, MARKET), or position/market status (OPEN, SETTLED) if we parse them. Keeps literals in one place and avoids typos.

**Reporting:**
- **Maven Surefire** (already configured): produces XML and TXT reports under `target/surefire-reports/` on every `mvn test`. No extra setup.
- **Optional later:** Allure, ExtentReports, or custom listeners if you want HTML dashboards, trends, or CI integration. Not in scope for Phase 1 unless you ask for it.

---

## 10. Pending inputs for JDBC / Kafka / E2E checks

Use this checklist before implementing DB and event-stream validations.

### A) JDBC verification inputs

- [ ] DB engine/version (Postgres/MySQL/etc.)
- [ ] Connection details per environment (host, port, db, user, password/secret source)
- [ ] SSL/TLS requirements
- [ ] Read-only access confirmation from test runner
- [ ] Tables/columns to validate (orders, positions, balances)
- [ ] API-to-DB mapping keys (order id, user id, market id)
- [ ] Assertion rules (exact match vs tolerance, immediate vs eventual)

### B) Kafka lag verification inputs

- [ ] Bootstrap servers
- [ ] Security mode and credentials/certs (PLAINTEXT/SSL/SASL)
- [ ] Topic names and partitions to monitor
- [ ] Consumer group id(s)
- [ ] Lag acceptance threshold (e.g. <= 0 or <= N within X sec)
- [ ] Check timing (after order, after resolve, after settlement)
- [ ] Data source choice (direct Kafka AdminClient vs metrics endpoint)

### C) End-to-end trading assertion inputs

- [ ] Exact golden-path flow for each run (login -> deposit -> order -> resolve -> redeem -> final balance)
- [ ] Trading enablement rule (inside automation vs pre-step)
- [ ] Expected response checks per step (status + required fields)
- [ ] Position checks (market, side, quantity, state transitions)
- [ ] Balance source of truth endpoint(s) and delta logic (e.g. +1 per winning share)
- [ ] Resolution mode (manual vs API) and max wait timeout
- [ ] Retry/idempotency behavior for partial/late states
