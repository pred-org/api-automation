# Pred UAT API reference and test cases

Full API map and test-case spec for the UAT environment at https://uat-frankfurt.pred.app.  
All portfolio endpoints require: `Authorization: Bearer <access_token>`.  
Access token is obtained via the existing auth/login flow.

**Implementation status:** Test case 1 (Platform health check) is implemented in `HealthCheckTest` (runs after Auth Flow in suite). Services expose all endpoints below; test cases 2-11 can be implemented using `UserService`, `PortfolioService`, `OrderService` (orderbook, place, cancel).

---

## Confirmed API endpoints

### AUTH / USER
- `GET /api/v1/user/me` -> user profile: id, pred_uid, is_enabled_trading, status, wallet addresses
- `GET /api/v1/user/maintainance` -> `{ downtime: false }`

### BALANCE
- `GET /api/v1/portfolio/balance` -> `{ usdc_balance, position_balance }` (global)
- `GET /api/v1/portfolio/balance?parent_market_id={id}&market_id={id}` -> same fields scoped to one market

### POSITIONS
- `GET /api/v1/portfolio/positions` -> `{ total_invested, total_to_win, position_league_summary[], positions[] }`
- `GET /api/v1/portfolio/positions?market_id={id}` -> same, scoped  
- Position object fields: market_id, market_name, alternate_name, parent_market_name, quantity, average_price, amount, side (long|short), status

### ORDERS
- `GET /api/v1/portfolio/open-orders` -> pending limit orders
- `GET /api/v1/portfolio/trade-history` -> all trades
- `GET /api/v1/portfolio/trade-history?market_id={id}` -> scoped  
- Trade object: activity (Open Long | Open Short | Redeemed), side, price, quantity, amount, pnl, transaction_hash, matched_at

### EARNINGS / PNL
- `GET /api/v1/portfolio/earnings` -> `{ user_id, realized_pnl, unrealized_pnl, total_pnl }`

### MARKET DATA (public, no auth)
- `GET /api/v1/order/{marketId}/orderbook/{marketId}` -> `{ bids[], asks[], metadata: { spread, mid_price, total_bid_quantity, total_ask_quantity } }`
- `GET /api/v1/market-discovery/fixtures?league_id={id}`

---

## Test cases to implement

### 1. PLATFORM HEALTH CHECK (pre-condition for all tests)
- Call `GET /user/maintainance` -> assert `downtime == false`
- Call `GET /user/me` -> assert `status == "ACTIVE"` and `is_enabled_trading == true`

### 2. BALANCE API ACCURACY
- Capture usdc_balance before and after a trade
- Assert: balance_after == balance_before - trade.amount (to within rounding tolerance)
- Capture position_balance; for SHORT positions it should be negative (e.g. -10 for 10 short shares)
- Global balance position_balance should be 0 when there are no open positions

### 3. MARKET ORDER — SHORT (confirmed working, liquidity exists on bid side)
- Pre: GET orderbook -> assert bids[0].quantity > 0
- Action: place SHORT market order for N shares via UI / POST order endpoint
- Post: balance decreased by trade.amount; position side=short, quantity=N; trade-history activity="Open Short"; earnings unrealized_pnl formula

### 4. MARKET ORDER — LONG (requires ask-side liquidity; mark test as "needs liquidity seeding")
- Pre: GET orderbook -> assert asks[0].quantity > 0
- Action: place LONG market order; post assertions same pattern as SHORT but side=long

### 5. LIMIT ORDER — PLACE & CANCEL
- Place limit order (Long or Short) at a specific price, GTC
- Post-place: GET open-orders -> order appears; GET balance -> balance NOT yet deducted
- Cancel the limit order
- Post-cancel: open-orders -> order gone; balance unchanged; trade-history -> no new entry

### 6. POSITION CLOSE (Reduce Only)
- Pre: open a SHORT position (e.g. 10 shares @ 30c)
- Action: place opposite (LONG) trade with "Reduce only", same quantity
- Post: positions -> position removed or quantity 0; earnings realized_pnl changed; trade-history new entry; balance reflects settlement

### 7. PNL ACCURACY — UNREALIZED
- After opening a position: GET positions (avg_price, quantity), GET orderbook (mid_price), GET earnings (unrealized_pnl)
- Assert: unrealized_pnl == (avg_price - mark_price) * quantity / 100 (SHORT); (mark_price - avg_price) * quantity / 100 (LONG)
- Assert: total_pnl == realized_pnl + unrealized_pnl

### 8. PNL ACCURACY — REALIZED (post-market resolution)
- When market is resolved (backend-triggered): trade-history "Redeemed" with pnl; earnings realized_pnl; positions resolved market gone; balance increased by redemption if won

### 9. PORTFOLIO VALUE MATH
- Portfolio value = usdc_balance + sum(position.amount) ≈ usdc_balance + total_invested (within $0.01)

### 10. ORDER BOOK INTEGRITY
- GET orderbook -> assert spread == ask_price - bid_price
- After placing SHORT at market: total_bid_quantity decreases by N
- mid_price between best bid and best ask

### 11. TRADE HISTORY FILTER
- GET trade-history -> count total; GET trade-history?market_id={id} -> count scoped
- Assert scoped count <= total; all scoped entries have matching market_id

---

## Key formulas for assertions

- Unrealized PnL SHORT = (avg_price - mark_price) * quantity / 100
- Unrealized PnL LONG  = (mark_price - avg_price) * quantity / 100
- Total PnL            = realized_pnl + unrealized_pnl
- Portfolio value      = usdc_balance + total_invested

---

## Implementation notes

- Market ID (BAR VS CEL): `0xf83d64fbb43a9b199109a96fee6291fc66b9fe0a5cd38b0bd2901fd10d7f1900`
- Orderbook is public (no auth); all portfolio and user endpoints need Bearer token
- Limit order has GTC (Good Till Cancelled) validity option
- "Reduce only" checkbox on trade panel for closing positions
- "Redeemed" activity in trade history = market resolution settlement (do not simulate; wait for backend)
- `/api/v1/portfolio/earnings` is the single source of truth for realized and unrealized PnL
- position_balance in /portfolio/balance: net share position (negative = net short)
