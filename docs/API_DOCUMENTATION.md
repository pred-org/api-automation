# API Documentation

Reference for all HTTP endpoints used by the API automation suite. Base URLs come from config: `API_BASE_URI_PUBLIC` (public API) and `API_BASE_URI_INTERNAL` (internal/admin). Authenticated calls use `Authorization: Bearer <access_token>` and `Cookie: refresh_token=<value>`.

---

## Trading flow: real base situation (spread)

There is always a spread: you cannot have LONG and SHORT at the same price (e.g. 50/50). Prices sit on either side of the spread (e.g. 49/51 or 35/70).

**Example:**

- User1 places **limit LONG at 35c, 100 shares** (wants to buy at 35).
- User1 places **limit SHORT at 70c, 100 shares** (wants to sell at 70).
- User1’s intent: profit by buying at 35 and selling at 70. User1 has no position yet.
- User2 provides liquidity: User2 takes the other side of both orders. User2 goes LONG 100 (e.g. matches User1’s SHORT at 70) and sells 100 at 35 (matches User1’s LONG at 35, so User2 is SHORT 100 at 35). So User2 ends with LONG 100 and SHORT 100; User1 ends with LONG 100 and SHORT 100. Both have offsetting positions, so both are effectively closed/flat.

In tests, a single mid price (e.g. 50) may be used only when the environment allows same-price matching for simplicity; in a real market, orders use different prices on each side of the spread.

---

## 1. Authentication (AuthService)

Base: public API (`getPublicBaseUri()`). Login returns `Set-Cookie: refresh_token=...`; send that cookie on every authenticated request.

### 1.1 Login with signature

**POST** `/api/v1/auth/login-with-signature`

**Headers**
- `Content-Type: application/json`
- `X-API-Key`: API key
- `Cookie: refresh_token=...` (optional; for refresh flow)

**Request body** (LoginRequest)
```json
{
  "data": {
    "wallet_address": "<EOA from sig-server>",
    "signature": "<EIP-712 or personal_sign from sig-server>",
    "message": "Sign in to PRED Trading Platform",
    "nonce": "<unique nonce>",
    "chain_type": "base-sepolia",
    "timestamp": <unix_seconds>
  }
}
```

**Response** (200)
- Body: `access_token`, `data.user_id`, `data.proxy_wallet_address` (or `data.proxy_wallet_addr`)
- Header: `Set-Cookie: refresh_token=...` (capture for later requests)

### 1.2 Refresh token

**POST** `/api/v1/auth/refresh/token`

**Headers**
- `Content-Type: application/json`
- `Cookie: refresh_token=<value>`

**Request body:** `{}`

**Response** (200): New `access_token` in body (e.g. `data.access_token` or `access_token`).

### 1.3 Create API key (internal)

**POST** `/api/v1/auth/internal/api-key/create`

**Base:** internal API. **Body:** `{}`. Response may contain `data.data.api_key` or `data.api_key`.

---

## 2. Orders (OrderService)

Base: public API. All order endpoints require auth (Bearer + Cookie) plus wallet headers.

**Common headers for order/portfolio**
- `Authorization: Bearer <access_token>`
- `Cookie: refresh_token=...`
- `X-Wallet-Address`: EOA address
- `X-Proxy-Address`: proxy (Safe) address (for place order)

### 2.1 Place order

**POST** `/api/v1/order/{marketId}/place`

**Headers:** As above (Bearer, Cookie, X-Wallet-Address, X-Proxy-Address).

**Request body** (PlaceOrderRequest)
| Field | Type | Description |
|-------|------|-------------|
| salt | string | Unique (e.g. timestamp) |
| user_id | string | From login |
| market_id | string | Market ID (hex) |
| side | string | "long" or "short" |
| token_id | string | Token ID (hex) |
| price | string | Price (e.g. "50" for 50c) |
| quantity | string | Quantity in shares |
| amount | string | Decimal; LONG: price * qty / 100, SHORT: (100 - price) * qty / 100 |
| is_low_priority | boolean | Optional |
| signature | string | From sig-server sign-order |
| type | string | "limit" |
| timestamp | long | Unix seconds |
| reduce_only | boolean | Optional, default false |
| fee_rate_bps | int | Optional, e.g. 0 |

**Response** (202): `order_id`, `status` ("open_order" or "matched").

### 2.2 Cancel order

**DELETE** `/api/v1/order/{marketId}/cancel`

**Headers:** Bearer + Cookie.

**Request body**
```json
{
  "order_id": "<uuid>",
  "market_id": "<marketId>"
}
```

### 2.3 Get orderbook (public)

**GET** `/api/v1/order/{marketId}/orderbook/{marketId}`

No auth. Returns `bids[]`, `asks[]`, `metadata` (e.g. spread, mid_price, total_bid_quantity, total_ask_quantity).

---

## 3. Portfolio (PortfolioService)

Base: public API. All endpoints require Bearer + Cookie.

### 3.1 Positions

**GET** `/api/v1/portfolio/positions`

**GET** `/api/v1/portfolio/positions?market_id={marketId}`

Returns list of positions (market_id, side, quantity, etc.).

### 3.2 Balance

**GET** `/api/v1/portfolio/balance`

Returns e.g. `usdc_balance` (may be in micro-USDC).

**GET** `/api/v1/portfolio/balance?parent_market_id={marketId}&market_id={marketId}`

Balance for a specific market.

### 3.3 Open orders

**GET** `/api/v1/portfolio/open-orders`

Returns pending limit orders. Response may have `data` or `open_orders` array.

### 3.4 Trade history

**GET** `/api/v1/portfolio/trade-history`

**GET** `/api/v1/portfolio/trade-history?market_id={marketId}`

Returns trades: activity (e.g. "Open Long", "Open Short", "Redeemed"), side, price, quantity, amount, pnl, matched_at.

### 3.5 PnL

**GET** `/api/v1/portfolio/pnl`

### 3.6 Earnings

**GET** `/api/v1/portfolio/earnings`

Response: user_id, realized_pnl, unrealized_pnl, total_pnl.

### 3.7 Internal balance (internal)

**GET** `/api/v1/balance/info`

**Base:** internal API. **Headers:** `X-User-ID`, `X-Trace-ID` (UUID). Returns balance info for the given user.

---

## 4. Enable trading (EnableTradingService)

Base: public API. Two-step flow: prepare then execute (sign transaction hash via sig-server sign-safe-approval).

### 4.1 Prepare

**POST** `/api/v1/user/safe-approval/prepare`

**Headers:** Bearer + Cookie.

**Request body**
```json
{
  "proxy_wallet_address": "<proxyWalletAddress>"
}
```

**Response:** Prepare data object to sign (transaction hash produced by backend).

### 4.2 Execute

**POST** `/api/v1/user/safe-approval/execute`

**Headers:** Bearer + Cookie.

**Request body**
```json
{
  "data": <prepareData from prepare response>,
  "signature": "<signature from sig-server sign-safe-approval>"
}
```

---

## 5. Deposit (DepositService)

Two-step flow: internal deposit returns `transaction_hash`, then public cashflow/deposit confirms with that hash.

### 5.1 Internal deposit

**POST** `/api/v1/competitions/internal/deposit?skip_updating_bs=true`

**Base:** internal API. **Headers:** Optional `Authorization: Bearer <INTERNAL_DEPOSIT_TOKEN>`.

**Request body** (DepositRequest)
```json
{
  "user_id": "<userId>",
  "amount": <amount>
}
```

**Response:** Contains `transaction_hash` (or `data.transaction_hash`) for step 2.

### 5.2 Cashflow deposit (public)

**POST** `/api/v1/cashflow/deposit`

**Headers:** Bearer + Cookie, `X-Proxy-Address`, `X-Wallet-Address` (EOA).

**Request body** (CashflowDepositRequest)
```json
{
  "salt": <long>,
  "transaction_hash": "<from step 1>",
  "timestamp": <unix>
}
```

---

## 6. Market discovery (MarketDiscoveryService)

Base: public API. No auth unless backend requires it.

### 6.1 Leagues

**GET** `/api/v1/market-discovery/leagues`

### 6.2 Fixtures by league

**GET** `/api/v1/market-discovery/fixtures?league_id={leagueId}`

---

## 7. Signature server (SignatureService)

Local/separate service (`SIG_SERVER_URL`). Used to produce EIP-712 and raw signatures for login and orders. Not the main backend.

### 7.1 Sign create proxy (login)

**POST** `{sigServerUrl}/sign-create-proxy`

**Request body**
```json
{
  "privateKey": "<optional; if omitted server may use env>"
}
```

**Response:** walletAddress, signature, message (for LoginRequest).

### 7.2 Sign order

**POST** `{sigServerUrl}/sign-order`

**Request body** (SignOrderRequest): salt, price, quantity, questionId (marketId), feeRateBps, intent (0 long, 1 short), signatureType, maker, signer, taker, expiration, nonce, priceInCents, timestamp, optional privateKey.

**Response:** ok, signature (used in PlaceOrderRequest).

### 7.3 Sign safe approval (enable trading)

**POST** `{sigServerUrl}/sign-safe-approval`

**Request body**
```json
{
  "transactionHash": "<from prepare response>",
  "usePersonalSign": false,
  "privateKey": "<optional>"
}
```

**Response:** signature for enable-trading execute (raw hash, not personal_sign).

---

## 8. Request/response schemas summary

| Model | Use |
|-------|-----|
| LoginRequest / LoginRequestData | wallet_address, signature, message, nonce, chain_type, timestamp |
| LoginResponse | access_token, data.user_id, data.proxy_wallet_address |
| PlaceOrderRequest | salt, user_id, market_id, side, token_id, price, quantity, amount, signature, type, timestamp, reduce_only, fee_rate_bps |
| SignOrderRequest | Fields for order signing (questionId, intent, maker, signer, taker, etc.) |
| SignOrderResponse | ok, signature |
| DepositRequest | user_id, amount |
| CashflowDepositRequest | salt, transaction_hash, timestamp |

---

## 9. Config / environment

- `API_BASE_URI_PUBLIC` – public API base URL
- `API_BASE_URI_INTERNAL` – internal API base URL
- `SIG_SERVER_URL` – signature server URL
- `API_KEY` – used for login and refresh
- `MARKET_ID`, `TOKEN_ID` – default market/token for order tests
- `INTERNAL_DEPOSIT_TOKEN` – optional for internal deposit
- `PRIVATE_KEY`, `EOA_ADDRESS` – for User1; `USER_2_*` / `.env.session2` for User2

All authenticated public endpoints expect Bearer token plus refresh cookie; order place also expects X-Wallet-Address and X-Proxy-Address.
