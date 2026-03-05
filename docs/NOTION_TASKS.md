# API Automation — Notion Task List

Copy these sections into Notion (or any task tracker). Tasks are ordered from zero to full flow: market redeem and balance check.

**Alignment:** See [OBJECTIVES_AND_ALIGNMENT.md](OBJECTIVES_AND_ALIGNMENT.md) for the shared objective (state-transition validation, two users, balance/settlement rules, test suites, success criteria). This task list implements that scope.

---

## Phase: Foundation (Zero)

**BaseService talk points (what goes in it):**
- Single class that wraps RestAssured; all HTTP from tests goes through it (no raw given/when in tests).
- Holds **base URI** (or two: public + internal) from ApiConfig; one place so we can switch env via config.
- Sets **default headers** (e.g. Content-Type: application/json, Accept: application/json); services can override per request.
- **Static block** that runs once: register LoggingFilter (and any other filters) so every request/response is logged and auth is masked.
- Exposes **generic methods**: e.g. `post(baseUri, path, body, headers)`, `get(baseUri, path, headers)`, `put`, `delete`; each returns RestAssured `Response`.
- No business logic: no login, no order building; only "send this request to this URI and return the response."
- AuthService, DepositService, OrderService, PortfolioService call BaseService methods with path + body; they do not construct RestAssured specs themselves.

| Done | Task | Notes |
|------|------|--------|
| [ ] | Create `src/main/java` package structure | `com.pred.apitests.base`, `.service`, `.model.request`, `.model.response`, `.filters`, `.listeners` |
| [ ] | Add **BaseService** | RestAssured wrapper: base URI (public/internal), default headers, register filter in static block |
| [ ] | Add **LoggingFilter** | Intercept request (log URI, headers, body); mask auth/token headers; then `ctx.next()`; log response status + body |
| [ ] | Attach LoggingFilter in BaseService | `RestAssured.filters(new LoggingFilter())` in static block |
| [ ] | Wire BaseApiTest to BaseService | Tests use BaseService for all HTTP; no raw given/when in tests for API calls |
| [ ] | Add log4j2 or keep Logback | FRAMEWORK.md suggests Log4j2; current project uses Logback — pick one and document |
| [x] | Add TestNG suite file | `suite.xml` for CI; used by `mvn test` (done) |
| [ ] | Add TestNG TestListener | onTestStart/Success/Failure logging; implement when adding listeners |

---

## Phase: Config and Auth Strategy

| Done | Task | Notes |
|------|------|--------|
| [ ] | Document auth strategy choice | Option A: Java calls sig-server for login + order. Option B: Pre-login from Node; env token/user/proxy; sig-server only for order sign |
| [ ] | Add config for API key | Env or properties: `API_KEY` / `api.key` for create-api-key and login header |
| [ ] | Add config for sig-server URL (if used) | e.g. `sig.server.url=http://localhost:5050` |
| [ ] | Add config for pre-login (if Option B) | `ACCESS_TOKEN`, `USER_ID`, `PROXY_WALLET`, `EOA_ADDRESS` from env; do not commit secrets |
| [ ] | Ensure market/token in config | `market.id`, `token.id` in testdata.properties or env; document how to set per run |
| [ ] | Add internal deposit auth (if required) | `INTERNAL_DEPOSIT_TOKEN` or similar for deposit API Bearer |

---

## Phase: Auth and Login

| Done | Task | Notes |
|------|------|--------|
| [ ] | Implement **Create API key** (internal) | POST `{internalBase}/api/v1/auth/internal/api-key/create`, body `{}`; capture API key |
| [ ] | Add **LoginRequest** POJO (optional) | Fields: wallet_address, signature, message, nonce, chain_type, timestamp; builder preferred |
| [ ] | Add **LoginResponse** POJO | Fields: access_token, user_id, proxy_wallet_address (or proxy_wallet_addr) |
| [ ] | Implement **AuthService.login** | POST `{publicBase}/api/v1/auth/login-with-signature`; headers X-API-Key, Content-Type; body from request/sig-server |
| [ ] | Integrate sig-server for login signature (if Option A) | Call sig-server endpoint for EIP-712 CreateProxy; use returned signature in login body |
| [ ] | Store token, user id, proxy after login | In test context or thread-local so deposit/order/portfolio can use them |
| [ ] | Test: login returns 200 and response has access_token, user_id, proxy | Assert status and required fields |

---

## Phase: Enable Trading (once per proxy)

| Done | Task | Notes |
|------|------|--------|
| [ ] | Implement **prepare** | POST `{publicBase}/api/v1/user/safe-approval/prepare`, Bearer token, body `{ "proxy_wallet_address": "<proxy>" }` |
| [ ] | Integrate EIP-1193 sign of transactionHash | Sig-server or Java: personal_sign(transactionHash) with EOA |
| [ ] | Implement **execute** | POST `{publicBase}/api/v1/user/safe-approval/execute`, body `{ "data": <from prepare>, "signature": "<from sign>" }` |
| [ ] | Test: enable-trading flow (prepare -> sign -> execute) succeeds | Or run once manually; then automation uses already-enabled proxy |

---

## Phase: Deposit

| Done | Task | Notes |
|------|------|--------|
| [ ] | Add **DepositRequest** POJO (optional) | user_id, amount (number) |
| [ ] | Implement **DepositService.deposit** | POST `{internalBase}/api/v1/competitions/internal/deposit`; body user_id (from login), amount (from config) |
| [ ] | Add internal auth header for deposit (if required) | e.g. Authorization: Bearer INTERNAL_DEPOSIT_TOKEN |
| [ ] | Test: deposit returns 2xx for valid user_id and amount | Use user_id from login; amount from ApiConfig.getDepositAmount() |

---

## Phase: Market and Place Order

| Done | Task | Notes |
|------|------|--------|
| [ ] | Read market ID and token ID from config | ApiConfig.getMarketId(), getTokenId(); fail fast if missing when running flow |
| [ ] | Add **PlaceOrderRequest** POJO (optional) | salt, market_id, side, token_id, price, quantity, amount, is_low_priority, signature, type, timestamp, expiration, reduce_only, fee_rate_bps |
| [ ] | Integrate sig-server for order signature (EIP-712) | Call sign-order with order params; use returned signature in place-order body |
| [ ] | Implement **OrderService.placeOrder** | POST `{publicBase}/api/v1/order/<market_id>/place`; headers Bearer, X-Wallet-Address (EOA), X-Proxy-Address (proxy); body from request |
| [ ] | Test: place order returns 202 | Use token and proxy from login; market/token from config |

---

## Phase: Positions (Verify)

| Done | Task | Notes |
|------|------|--------|
| [ ] | Implement **PortfolioService.getPositions** | GET `{publicBase}/api/v1/portfolio/positions`; Authorization: Bearer token |
| [ ] | Add **PositionsResponse** or position item POJO (optional) | For type-safe assertions: market, side, size, state (open/settled/redeemed) |
| [ ] | Test: after place order, positions list contains position for market | Assert position exists; correct side/size if needed |
| [ ] | Document or implement poll until position appears | If eventual consistency; optional retry with timeout |

---

## Phase: Market Resolution and Redeem

| Done | Task | Notes |
|------|------|--------|
| [ ] | Document resolution method | Manual close/resolve vs API; automation only verifies after resolved |
| [ ] | Add **market status** API (when available) | Poll until market resolved; endpoint TBD per API_REFERENCE |
| [ ] | Implement poll until market resolved | Retry GET market status until resolved or timeout |
| [ ] | After resolution: get positions again | Verify position state: settled/redeemed as per API |
| [ ] | Test: position shows settled/redeemed after resolution | Assert state transition; no duplicate settlement |

---

## Phase: Balance Check (Final Verification)

| Done | Task | Notes |
|------|------|--------|
| [ ] | Implement **PortfolioService.getBalance** | GET `{publicBase}/api/v1/portfolio/balance`; Bearer token |
| [ ] | Implement getBalance by market (optional) | GET with query parent_market_id, market_id for reserved/position balance |
| [ ] | Add **BalanceResponse** POJO | success, usdc_balance, position_balance (or as per API) |
| [ ] | Capture balance before flow (after deposit) | Store initial balance for delta check |
| [ ] | Capture balance after settlement | After resolution and redeem |
| [ ] | Assert balance accuracy | e.g. long on winning outcome = +$1 per share; initial + PnL = final |
| [ ] | Assert no negative balance | Final balance >= 0 (or per business rules) |
| [ ] | Assert no duplicate settlement | Same position not credited twice (state or idempotency check if applicable) |

---

## Phase: End-to-End Test and CI

| Done | Task | Notes |
|------|------|--------|
| [ ] | Write **single E2E test**: login -> deposit -> place order -> (wait/poll resolution) -> positions -> balance | One test class or ordered test methods |
| [ ] | Apply Phase 1 validation criteria | Position correctness; balance accuracy; no duplicate settlement; no negative balance |
| [ ] | Make E2E runnable via config only | No manual steps; env/properties for base URLs, credentials, market, amount |
| [x] | Add GitHub Actions workflow | `.github/workflows/api-tests.yml`; Java 17, mvn clean test on push/PR (done). Enable when repo is on GitHub. |
| [ ] | Add secrets in GitHub repo (when needed) | API_BASE_URI_PUBLIC, API_BASE_URI_INTERNAL, ACCESS_TOKEN, MARKET_ID, TOKEN_ID, etc. |
| [ ] | Optional: upload Surefire/Allure artifacts | Uncomment upload-artifact steps in workflow |
| [ ] | Optional: scheduled run (cron) | Uncomment schedule in workflow; e.g. 11:30 PM IST = 18:00 UTC |
| [ ] | Optional: test reporter in Actions | e.g. dorny/test-reporter for surefire XML summary |

---

## Phase: Two-User Flow and Test Suites (per OBJECTIVES_AND_ALIGNMENT)

| Done | Task | Notes |
|------|------|--------|
| [ ] | Second test user (User B) | EOA, login, enable trading, deposit; reuse same flow as User A |
| [ ] | Two-user matching flow | User A BUY, User B SELL, same market/price; assert positions and reserved for both |
| [ ] | Suite 1 — Order Validation | Valid order; invalid payload; duplicate salt; expired order |
| [ ] | Suite 2 — Matching Engine | Full match; partial match; multiple matches; cancel order |
| [ ] | Suite 3 — Balance Engine | Reserved logic; position creation; settlement correctness; no negative balance |
| [ ] | Suite 4 — Settlement | Winning long; winning short; losing case; double redeem prevention |
| [ ] | Balance assertions from state | available = total - reserved; never hardcode balances |
| [ ] | Partial fill scenario | e.g. A buy 100, B sell 40; assert position 40, open 60, reserved correct |
| [ ] | Critical validations | Duplicate salt, invalid sig, expired ts, insufficient balance, double redeem, cancel releases reserved |

---

## Quick Reference: Flow Order

Use this list in Notion (or as a sub-checklist). Under each number, put the tasks listed.

**1. Foundation (BaseService, filter, config)**  
Put here: Create `src/main/java` packages (base, service, model.request, model.response, filters, listeners). Add BaseService (RestAssured wrapper, base URI, register filter). Add LoggingFilter (log request/response, mask auth). Attach filter in BaseService. Wire BaseApiTest to BaseService. Add or keep logging (Log4j2/Logback). Add TestNG suite.xml and optional TestListener.

**2. Config and auth strategy**  
Put here: Choose auth strategy (sig-server from Java vs pre-login + env). Add config for API key, sig-server URL (if used), pre-login token/user/proxy (if Option B). Add market.id, token.id, deposit amount. Add internal deposit auth token if backend requires it. All from env/properties; no secrets in code.

**3. Login (create API key -> login with signature -> store token, user, proxy)**  
Put here: Create API key (internal API). LoginRequest/LoginResponse POJOs. AuthService.login. Sig-server for login signature if Option A. Store access_token, user_id, proxy_wallet_address for later steps. Test: 200, token and proxy present.

**4. Enable trading (prepare -> sign txHash -> execute)**  
Put here: Prepare (safe-approval/prepare). EIP-1193 sign transactionHash. Execute (safe-approval/execute). Test or run once per proxy.

**5. Deposit (user_id, amount)**  
Put here: DepositRequest POJO (optional). DepositService.deposit. Internal auth header if required. Test: 2xx, balance increases; capture initial_total_balance.

**6. Place order (market, token, signature)**  
Put here: PlaceOrderRequest POJO (optional). Sig-server for order signature (EIP-712). OrderService.placeOrder. Test: 202 accepted; reserved balance increases; no position yet if unmatched.

**7. Get positions (verify position exists)**  
Put here: PortfolioService.getPositions. Position POJO (optional). Assert position exists for market; side/size correct. Optional: poll until position appears.

**8. Resolve market (manual or API); poll status until resolved**  
Put here: Document how resolution is done. Market status API (when available). Poll until resolved or timeout.

**9. Get positions again (verify settled/redeemed)**  
Put here: Call getPositions after resolution. Assert position state = settled/redeemed; no duplicate settlement.

**10. Get balance; assert accuracy, no negative, no duplicate settlement**  
Put here: PortfolioService.getBalance. BalanceResponse POJO. Capture balance before/after. Assert final = initial + pnl (or - cost); no negative; redeem once.
