# What You Need To Start Automation

Checklist of what must be in place before automation can run. No secrets in code; everything from env or secure config.

---

## 1. Environment Access

| Item | Source | Notes |
|------|--------|--------|
| **Public API base URL** | Env: `API_BASE_URI_PUBLIC` or `api.base.uri.public` | e.g. `https://uat-frankfurt.pred.app` for login, enable-trading, place order, portfolio |
| **Internal API base URL** | Env: `API_BASE_URI_INTERNAL` or `api.base.uri.internal` | e.g. `http://api-internal.uat-frankfurt.pred.app` for create API key, deposit |
| **Network reachability** | Manual / CI | Automation host can call both URLs (no firewall blocking) |

---

## 2. API Key and Login (per user)

For **each test user** (User A, User B):

| Item | Source | Notes |
|------|--------|--------|
| **API key** | Create once via internal API, or env `API_KEY` | Used as `X-API-Key` in login. Create: POST `{internalBase}/api/v1/auth/internal/api-key/create` |
| **EOA address** | Env (e.g. `USER_A_EOA`, `USER_B_EOA`) | Wallet address that signs login and orders |
| **Private key** | Env or sig-server config only; never in repo | Used to produce EIP-712 (login, order) and EIP-1193 (enable-trading) signatures |

**Signing option (pick one):**

- **Option A — Sig-server:** Node sig-server running (e.g. `http://localhost:5050`). Java calls it to get login and order signatures. Env: `SIG_SERVER_URL`.
- **Option B — Pre-login:** Run Node scripts once to get token/user/proxy; set `ACCESS_TOKEN`, `USER_ID`, `PROXY_WALLET` (and optionally `EOA_ADDRESS`) in env. Automation only needs sig-server for **order** signature, or pre-signed order payloads.

---

## 3. Enable Trading and Deposit

| Item | Source | Notes |
|------|--------|--------|
| **Enable trading** | One-time per proxy | Prepare -> sign `transactionHash` (EIP-1193) -> execute. Either in automation or run Node script once; then reuse same proxy. |
| **Deposit auth** | Env (if required): `INTERNAL_DEPOSIT_TOKEN` or `DEPOSIT_AUTH_TOKEN` | Bearer token for internal deposit API. Confirm with backend whether deposit needs auth. |
| **Deposit amount** | Env: `DEPOSIT_AMOUNT` or `deposit.amount` | e.g. `1000000000` (same units as pred-load-tests). Used to fund each user. |

---

## 4. Market and Order Test Data

| Item | Source | Notes |
|------|--------|--------|
| **Market ID** | Env: `MARKET_ID` or `market.id` | Active market for order placement and matching. Must exist in target environment. |
| **Token ID** | Env: `TOKEN_ID` or `token.id` | Outcome token for the market. Required for place-order body. |
| **Order params** (optional overrides) | Env or `testdata.properties`: `order.side`, `order.price`, `order.quantity`, `order.type` | Defaults in ApiConfig; override for different scenarios. |

---

## 5. API Contracts (What We Have vs TBD)

| Area | Status | Reference |
|------|--------|-----------|
| Create API key | Documented | API_DOCUMENTATION.md |
| Login (login-with-signature) | Documented | Same; EIP-712 CreateProxy |
| Enable trading (prepare / execute) | Documented | Same; EIP-1193 for txHash |
| Deposit | Documented | Same; body user_id, amount |
| Place order | Documented | Same; EIP-712 Order |
| Get positions | Documented | GET portfolio/positions |
| Get balance | Documented | GET portfolio/balance (and optional by market) |
| **Market status (resolved?)** | TBD | Endpoint or field to poll until market resolved; confirm with backend. |
| **Balance response shape** | Partially documented | Confirm exact JSON fields: total_balance, available_balance, reserved_balance, position_balance. |
| **Positions response shape** | Partially documented | Confirm fields for market, side, size, state (open/settled/redeemed). |

**Before full settlement tests:** Get from backend or API docs: (1) how to know when market is resolved, (2) exact balance and position response fields for assertions.

---

## 6. Tooling (Already in Place)

- **Java 17+**, **Maven 3.6+**
- **TestNG** (suite: `src/test/resources/suite.xml`)
- **RestAssured**, **AssertJ**, **Jackson**
- **GitHub Actions** workflow (optional; enable when repo is on GitHub)

No extra install needed to run `mvn test` for existing smoke tests.

---

## 7. Decisions You Must Make

| Decision | Options | Impact |
|----------|---------|--------|
| **Auth/signing** | Sig-server from Java vs pre-login + env token | Drives whether automation calls Node for every login/order or only for order sign. |
| **Two users** | Two EOAs + two private keys (or two sets of env tokens) | Required for matching tests (User A buy, User B sell). |
| **Market resolution** | Manual vs API | If manual: automation polls until resolved then asserts. If API: automation can trigger resolve when available. |
| **Environment** | UAT vs other | Set `API_BASE_URI_PUBLIC` and `API_BASE_URI_INTERNAL` for that env. |

---

## 8. Minimal Set To Run First Flow (Single User)

To run **login -> deposit -> place order -> get positions -> get balance** for one user you need:

1. Public and internal base URLs (env).
2. One API key (create once or from env).
3. One EOA + private key (or pre-login token/user/proxy in env).
4. Sig-server running **or** pre-login credentials in env.
5. Enable trading done once for that proxy.
6. Deposit auth (if required by backend).
7. `DEPOSIT_AMOUNT`, `MARKET_ID`, `TOKEN_ID` in env.
8. Confirmed balance/positions response shape for assertions.

---

## 9. To Run Two-User Matching

Everything above plus:

- Second user: second EOA + private key (or second set of token/user/proxy).
- Same market and matching price for User A (buy) and User B (sell).
- Balance and position assertions as in OBJECTIVES_AND_ALIGNMENT (no hardcoded balances).

---

## 10. Where This Is Documented in the Repo

| Need | Doc |
|------|-----|
| Endpoints, headers, bodies | [API_DOCUMENTATION.md](API_DOCUMENTATION.md) |
| Env/config mapping | `docs/CONFIG_AND_TEST_DATA.md` and `.env.template` |
| What to validate (state, balances, settlement) | [OBJECTIVES_AND_ALIGNMENT.md](OBJECTIVES_AND_ALIGNMENT.md) |
| Implementation tasks | [NOTION_TASKS.md](NOTION_TASKS.md) |
| Framework (services, POJOs, filters) | [FRAMEWORK.md](FRAMEWORK.md) |
| Config keys (base URI, market, deposit, order) | `ApiConfig.java`; `src/test/resources/testdata.properties` |
