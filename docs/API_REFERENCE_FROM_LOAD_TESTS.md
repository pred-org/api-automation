# API reference (from pred-load-tests)

This document summarizes APIs and flow from the **pred-load-tests** project so api-automation (RestAssured) can call the same backends. Source of truth: `pred-load-tests/docs/FLOW_API_REFERENCE.md` and the Node scripts under `sig-server/api/` and `deposit/`.

**Important:** Login and place-order require **EIP-712 / EIP-1193 signatures**. Options for automation: (1) call the existing **sig-server** (Node) from Java for signatures, or (2) provide pre-generated tokens/signatures via env. Create API key uses the **internal** base URL; login, enable-trading, and place-order use the **public** base URL; deposit uses the **internal** URL.

---

## Base URLs

| Use | URL |
|-----|-----|
| Public API (login, prepare, execute, place order) | `https://uat-frankfurt.pred.app` |
| Internal API (create API key, deposit) | `http://api-internal.uat-frankfurt.pred.app` |

Override via config/env in api-automation (e.g. for different environments).

---

## 1. Create API key

| Item | Value |
|------|--------|
| Method | POST |
| URL | `{internalBase}/api/v1/auth/internal/api-key/create` |
| Headers | `Content-Type: application/json` |
| Body | `{}` |

**Response:** API key string. Use as `X-API-Key` in login.

**In pred-load-tests:** `sig-server/api/create-api-key.js`

---

## 2. Login (login-with-signature)

| Item | Value |
|------|--------|
| Method | POST |
| URL | `{publicBase}/api/v1/auth/login-with-signature` |
| Headers | `Content-Type: application/json`, `X-API-Key: <api_key>` |
| Body | `data.wallet_address` (EOA), `data.signature` (EIP-712 CreateProxy), `data.message`, `data.nonce`, `data.chain_type`, `data.timestamp` |

**Signature:** EIP-712 CreateProxy (domain "Pred Contract Proxy Factory", chainId 84532). Signed with wallet private key. See `pred-load-tests/sig-server/signatures/login.js`.

**Response:** `access_token` (Bearer for later steps), `user_id` (in `data` or top level), `proxy_wallet_address` / `proxy_wallet_addr` (in `data` or top level). These are the **access token**, **user id**, and **proxy wallet** (privy/proxy) needed for the flow.

**In pred-load-tests:** `sig-server/signatures/login.js`, `sig-server/api/get-access-token.js`

---

## 3. Enable trading (prepare + sign txHash + execute)

Required once per proxy before placing orders.

- **Prepare:** POST `{publicBase}/api/v1/user/safe-approval/prepare`, header `Authorization: Bearer <access_token>`, body `{ "proxy_wallet_address": "<proxy>" }`. Response includes `transactionHash` and full `data` for execute.
- **Sign:** EIP-1193 sign the `transactionHash` (personal_sign of the hash) with the same EOA that owns the proxy.
- **Execute:** POST `{publicBase}/api/v1/user/safe-approval/execute`, header `Authorization: Bearer <access_token>`, body `{ "data": <from prepare>, "signature": "<from sign>" }`.

**In pred-load-tests:** `sig-server/execution/enable-trading.js`, `docs/FLOW_API_REFERENCE.md` Step 3.

---

## 4. Deposit (internal)

| Item | Value |
|------|--------|
| Method | POST |
| URL | `{internalBase}/api/v1/competitions/internal/deposit` |
| Headers | `Content-Type: application/json`; optional `Authorization: Bearer <token>` if internal API requires auth |
| Body | `{ "user_id": "<user_id_from_login>", "amount": <number> }` |

**Response:** 2xx on success. Amount units as in pred-load-tests (e.g. 1000000000).

**In pred-load-tests:** `deposit/deposit-funds.js`, `deposit/README.md`. Env: `INTERNAL_DEPOSIT_TOKEN` or `DEPOSIT_AUTH_TOKEN` for Bearer if needed.

---

## 5. Place order

| Item | Value |
|------|--------|
| Method | POST |
| URL | `{publicBase}/api/v1/order/<market_id>/place` |
| Headers | `Content-Type: application/json`, `Authorization: Bearer <access_token>`, `X-Wallet-Address: <EOA>`, `X-Proxy-Address: <proxy>` |
| Body | `salt`, `market_id`, `side` ("long" / "short"), `token_id`, `price`, `quantity`, `amount`, `is_low_priority`, `signature` (EIP-712 Order), `type` ("limit" / "market"), `timestamp`, `expiration`, `reduce_only`, `fee_rate_bps` |

**Signature:** EIP-712 Order (domain "Pred CTF Exchange", chainId 84532). Signed with EOA; maker is proxy. See `pred-load-tests/sig-server/signatures/server.js` (`/sign-order`).

**Response:** 202 on success.

**In pred-load-tests:** `sig-server/api/place-order.js`. Config: `MARKET_ID`, `TOKEN_ID`; order params: price (cents), quantity, etc.

---

## 6. Get positions (portfolio)

| Item | Value |
|------|--------|
| Method | GET |
| URL | `{publicBase}/api/v1/portfolio/positions` |
| Headers | `Authorization: Bearer <access_token>`, `Content-Type: application/json`, `Accept: */*` |
| Query params | None required; user is inferred from the Bearer token. |

**Response:** 200 OK with JSON body. Response shape should list positions for the authenticated user; use it to assert position exists for a market and to check open vs settled/redeemed when verifying after resolution. Do not hardcode user ids or tokens in code; use the token from login (or env) for the request.

---

## 7. Get balance (portfolio)

**7a. Overall balance (no query params)**

| Item | Value |
|------|--------|
| Method | GET |
| URL | `{publicBase}/api/v1/portfolio/balance` |
| Headers | `Authorization: Bearer <access_token>`, `Content-Type: application/json`, `Accept: */*` |
| Query params | None; user is inferred from the Bearer token. |

**Response:** 200 OK, JSON body. Use for final balance verification after settlement. Do not hardcode user ids or tokens in code; use the token from login (or env) for the request.

**7b. Balance by market (reserved / position balance)**

Same path with query params to get balance scoped to a market (e.g. reserved balance, position balance for that market).

| Item | Value |
|------|--------|
| Method | GET |
| URL | `{publicBase}/api/v1/portfolio/balance?parent_market_id=<market_id>&market_id=<market_id>` |
| Headers | `Authorization: Bearer <access_token>`, `Content-Type: application/json`, `Accept: */*` |
| Query params | `parent_market_id` (market id from config), `market_id` (same market id; use config value, do not hardcode). |

**Response (example):** `{ "success": true, "usdc_balance": "<string>", "position_balance": "<string>" }`. Use for market-specific balance checks (e.g. reserved balance, position balance for the traded market).

**Alternative: internal balance API**

| Item | Value |
|------|--------|
| Method | GET |
| URL | `{internalBase}/api/v1/balance/info` |
| Headers | `X-User-ID: <user_id_from_login>`, `X-Trace-ID: <uuid>` (e.g. generate a UUID per request for tracing) |

Use when calling the internal API (e.g. from Postman or backend-style checks). User id must come from login response or config; do not hardcode. X-Trace-ID can be any UUID.

---

## 8. Market status (to be confirmed)

Endpoint or field to know when a market is resolved. To be added when available.

---

## Config / env mapping (pred-load-tests to api-automation)

| pred-load-tests (config.js / env) | api-automation use |
|-----------------------------------|--------------------|
| API_KEY | Create once; use for login header X-API-Key |
| PRIVATE_KEY, EOA_ADDRESS | Used by sig-server to produce login and order signatures; or supply pre-signed token for automation |
| USER_ID, PROXY | From login response; use in deposit (user_id), place order (X-Proxy-Address), etc. |
| MARKET_ID, TOKEN_ID | Market and outcome for order; keep in config (you change as needed) |
| INTERNAL_DEPOSIT_TOKEN | If deposit API requires Bearer auth |

---

## Using the Node sig-server from Java

For a single flow (one user, one order), options:

1. **Run sig-server alongside:** Start `cd sig-server && npm start` (Node). From RestAssured, call `http://localhost:5050/sign-create-proxy-mm` and `http://localhost:5050/sign-order` with the same payloads as the Node scripts, then use returned signatures in login and place-order. Credentials (PRIVATE_KEY, EOA, etc.) stay in Node config; Java only does HTTP to APIs + sig-server.
2. **Pre-login from Node:** Run `get-access-token.js` once; put `TOKEN`, `USER_ID`, `PROXY` in env. Run enable-trading once. Then Java uses env token/proxy and only needs to call sig-server for **order signature** (sign-order) before place-order.
3. **Fully from Java:** Implement or shell out EIP-712/EIP-1193 signing in Java (larger change).

Recommendation: start with (1) or (2) so api-automation reuses the same flow and signatures as pred-load-tests.

---

## Refresh token (HTTP cookie)

Refresh works via **HTTP cookie**, not a request body. The browser sends it automatically; in Java/RestAssured we must pass it explicitly.

- **After login:** Capture `Set-Cookie: refresh_token=...` from the response and store it (e.g. in TokenManager).
- **Every authenticated request:** Send `Cookie: refresh_token=<stored_value>` along with `Authorization: Bearer <access_token>`.
- **When access_token is near expiry (~40 min):** Call login again with the stored refresh cookie; the server issues a new access_token (and may set a new refresh cookie). No separate refresh endpoint.

---

## Complete API contract (summary)

| # | Endpoint | Auth | Cookie | Notes |
|---|----------|------|--------|--------|
| 1 | POST {internal}/api/v1/auth/internal/api-key/create | None | No | Body: `{}`. Response: plain string (API key). |
| 2 | POST {public}/api/v1/auth/login-with-signature | X-API-Key | Optional on first login | Body: data.wallet_address, data.signature (EIP-712), data.message, data.nonce, data.chain_type, data.timestamp. Response: access_token + user context. **Capture Set-Cookie: refresh_token.** |
| 3 | POST {public}/api/v1/user/safe-approval/prepare | Bearer | Yes | Body: `{ "proxy_wallet_address": "..." }`. Response: full data + transactionHash. |
| 4 | POST {public}/api/v1/user/safe-approval/execute | Bearer | Yes | Body: data (from prepare), signature (EIP-1193 of transactionHash). |
| 5 | POST {public}/api/v1/order/{market_id}/place | Bearer, X-Wallet-Address, X-Proxy-Address | Yes | Body: salt, user_id, market_id, side, token_id, price, quantity, amount, is_low_priority, signature (EIP-712 Order), type, timestamp, reduce_only, fee_rate_bps. Response: 202. |
| 6 | POST {internal}/api/v1/competitions/internal/deposit | Optional Bearer (INTERNAL_DEPOSIT_TOKEN) | No | Body: user_id, amount. |
| 7 | GET {public}/api/v1/portfolio/positions | Bearer | Yes | User from token. |
| 8 | GET {public}/api/v1/portfolio/balance | Bearer | Yes | Overall balance. |
| 9 | GET {public}/api/v1/portfolio/balance?parent_market_id=&market_id= | Bearer | Yes | Balance by market. |
| 10 | GET {internal}/api/v1/balance/info | X-User-ID, X-Trace-ID | No | Internal only. |

---

## Three signatures (summary)

| Where | Type | Who signs | What is signed |
|-------|------|-----------|----------------|
| Login | EIP-712 CreateProxy | EOA private key | Typed data — domain "Pred Contract Proxy Factory", chainId 84532 |
| Enable trading execute | EIP-1193 | EOA private key | transactionHash from prepare (personal_sign) |
| Place order | EIP-712 Order | EOA private key | Typed data — domain "Pred CTF Exchange", chainId 84532 |

All three are produced by the Node sig-server; Java calls sig-server for each.

---

## Build list (from this contract)

- **TokenManager:** store refreshToken, tokenSetAt; isTokenExpiringSoon() (true if &gt; 40 min); send Cookie on requests.
- **AuthService:** capture Set-Cookie: refresh_token from login response; pass Cookie on login (for re-login refresh).
- **SignatureService:** call sig-server for login signature and order signature.
- **EnableTradingService:** prepare + execute (with EIP-1193 sign of transactionHash).
- **DepositService:** internal deposit.
- **OrderService:** place order.
- **PortfolioService:** positions, balance (overall and by market).
- **POJOs:** PrepareResponse, PlaceOrderRequest, DepositRequest, BalanceResponse, PositionsResponse.
