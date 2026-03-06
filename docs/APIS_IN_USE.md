# APIs in use (api-automation)

List of all API endpoints used by the project. Base URLs come from config: `api.base.uri.public`, `api.base.uri.internal`, and (for sig-server) `sig.server.url`.

---

## 1. Pred backend – Public base

Base: `Config.getPublicBaseUri()` (e.g. `https://uat-frankfurt.pred.app`).  
Auth: `Authorization: Bearer <access_token>` and refresh cookie unless noted.

| # | Method | Path | Service / usage |
|---|--------|------|-----------------|
| 1 | POST | `/api/v1/auth/login-with-signature` | AuthService – login; body: wallet_address, signature, message, nonce, chain_type, timestamp; header: X-API-Key |
| 2 | POST | `/api/v1/user/safe-approval/prepare` | EnableTradingService – prepare safe approval |
| 3 | POST | `/api/v1/user/safe-approval/execute` | EnableTradingService – execute safe approval (data + signature from prepare) |
| 4 | POST | `/api/v1/cashflow/deposit` | DepositService – deposit with transaction_hash from internal deposit |
| 5 | POST | `/api/v1/order/{marketId}/place` | OrderService – place order (body: salt, market_id, side, token_id, price, quantity, amount, signature, etc.) |
| 6 | DELETE | `/api/v1/order/{marketId}/cancel` | Cancel order; body: `order_id` (UUID), `market_id` (hex) |
| 7 | GET | `/api/v1/portfolio/positions` | Portfolio – list positions |
| 8 | GET | `/api/v1/portfolio/balance` | PortfolioService – overall balance |
| 9 | GET | `/api/v1/portfolio/balance?parent_market_id={id}&market_id={id}` | PortfolioService – balance by market |
| 10 | GET | `/api/v1/portfolio/earnings` | Portfolio – earnings; used for PnL data. Response: user_id, realized_pnl, unrealized_pnl, total_pnl. Realized = PnL when position closed; unrealized = open position PnL from mark price. |
| 11 | GET | `/health` | HealthCheckTest – health check (test disabled by default) |

---

## 2. Pred backend – Internal base

Base: `Config.getInternalBaseUri()` (e.g. `http://api-internal.uat-frankfurt.pred.app`).  
Auth: per-endpoint (see below).

| # | Method | Path | Service / usage |
|---|--------|------|-----------------|
| 13 | POST | `/api/v1/auth/internal/api-key/create` | AuthService – create API key; body: `{}`; no Bearer |
| 14 | POST | `/api/v1/competitions/internal/deposit?skip_updating_bs=true` | DepositService – internal deposit; headers: X-User-ID, Content-Type; optional Bearer (INTERNAL_DEPOSIT_TOKEN) |
| 15 | GET | `/api/v1/balance/info` | PortfolioService – internal balance; headers: X-User-ID, X-Trace-ID (UUID); no Bearer |

---

## 3. Sig-server (local Node)

Base: `sig.server.url` (e.g. `http://localhost:5050`).  
Used for EIP-712 / EIP-1193 signatures; not the Pred backend.

| # | Method | Path | Service / usage |
|---|--------|------|-----------------|
| 16 | POST | `/sign-create-proxy` | SignatureService – CreateProxy signature for login; body: optional `privateKey` |
| 17 | POST | `/sign-order` | SignatureService – order signature for place-order; body: SignOrderRequest |
| 18 | POST | `/sign-safe-approval` | SignatureService – safe approval signature for enable-trading; body: transactionHash, etc. |

---

## 4. External (non-Pred)

| # | Method | URL | Usage |
|---|--------|-----|--------|
| 19 | POST | `https://slack.com/api/chat.postMessage` | SlackNotificationListener – test run summary to Slack; Bearer token (Bot token) |

---

## 5. Not in use yet (specs to be provided)

| API | Notes |
|-----|--------|
| **Trade histories** | Method, path, base, auth to be provided. |

(All requests use project config and user credentials; no tokens are stored in this doc.)

---

## Summary counts

- **Public Pred:** 11 endpoints (10 in use + 1 health disabled): auth, enable-trading, deposit, place order, cancel order, positions, balance, balance by market, earnings (PnL), health
- **Internal Pred:** 3 endpoints
- **Sig-server:** 3 endpoints
- **External:** 1 (Slack)
- **To be added (you provide specs):** Trade histories

Total: **18** distinct APIs in use or referenced.
