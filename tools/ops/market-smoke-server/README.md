# market-smoke-server

This service exposes a single HTTP endpoint that authenticates two wallets, discovers every active market for a fixture canonical name, and for each market places a matching long (user 1) and short (user 2) limit order so ops can confirm end-to-end tradeability on the configured PRED environment (including on-chain settlement path via the normal API flow).

## Prerequisites

- Node.js 18+ recommended
- The PRED **sig-server** running (default `http://localhost:5050`) for EIP-712 `sign-create-proxy` and `sign-order`
- Both wallets must already be **enabled for trading** on the target environment (this tool does not run enable-trading or deposits)

## Setup

```bash
cd tools/ops/market-smoke-server
cp .env.example .env
# Edit .env if you use non-default PRED or sig-server URLs
npm install
```

## Run

```bash
node server.js
```

The server listens on port **5051** by default (`PORT` in `.env`).

## Postman

1. Create a `POST` request to `http://localhost:5051/verify-markets`
2. Header: `Content-Type: application/json`
3. Body (raw JSON), for example:

```json
{
  "canonicalName": "ind-vs-pak-ipl-2026",
  "privateKey1": "0x...",
  "privateKey2": "0x..."
}
```

- `canonicalName`: fixture canonical name as returned by market discovery
- `privateKey1`: hex private key for user 1 (places **long** orders)
- `privateKey2`: hex private key for user 2 (places **short** orders to match)

## Example success response (shape)

The response includes `canonicalName`, `fixtureName`, `totalMarkets`, `passed`, `failed`, `timeTakenMs`, and a `results` array with one object per market (`status`, `user1Order`, `user2Order`, `positionCreated`, `timeTakenMs`, etc.).

## Error responses

| HTTP | Meaning |
|------|---------|
| 400 | Missing or invalid body fields |
| 401 | Auth failed for user 1 or user 2 (login or API key step) |
| 404 | Discover returned no active markets for the canonical name |
| 503 | sig-server is not reachable at `SIG_SERVER_URL` |

Individual market failures are returned as `status: "failed"` entries inside `results`; the HTTP status for a completed run is still **200** when the handler finishes.

## Notes

- Orders use fixed UAT token id `0x1234567890abcdef1234567890abcdef12345678`, price `30` (cents), quantity `100` (shares). Limit `amount` is **LONG:** `price * quantity / 100` (e.g. `30.00`), **SHORT:** `(100 - price) * quantity / 100` (e.g. `70.00`). See `docs/API_DOCUMENTATION.md`.
- EIP-712 `sign-order` must use **intent 0** for long and **intent 1** for short so signature recovery matches `X-Wallet-Address` (same as `OrderFlowTest`).
- Place-order URL path uses `parent_market_id` from discovery; the JSON body `market_id` is the sub-market (outcome) id.
- Markets are processed **sequentially** to reduce load on the public API.
- If `GET /api/v1/portfolio/positions?market_id=...` returns **500**, `positionCreated` is set to the string `not_verified_positions_api_500` while still reporting successful place-order HTTP statuses when those succeeded.

## Health

`GET /health` returns `{ "ok": true, "service": "market-smoke-server" }`.
