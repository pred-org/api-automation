# API Automation – Test Cases

List of all test cases in the suite and what each one verifies. Tests run in the order defined in `src/test/resources/suite.xml`. Typical run: 34 test methods (33 reported: `printAccessTokenForPostman` is a utility, excluded from Slack and from test count), 0 failures, 2 skipped (enableTrading_success, depositFunds when balance already sufficient).

**Amount convention:** Prices are 0–100 cents per share. The API expects `amount` in USDC (dollars): **amount = price * quantity / 100** (2 decimals). For short orders: **amount = (100 - price) * quantity / 100**.

**Market ID (testdata.properties / MARKET_ID):** All order and balance-by-market tests use `Config.getMarketId()`. The suite does **not** assert that this market exists or is tradeable; it only asserts HTTP status (200/202) and basic response shape. If the API returns 200 for balance-by-market or 202 for place-order even when the market is invalid or closed, those tests will pass. So **`market.id` in testdata.properties (or env MARKET_ID) must be a valid, tradeable market ID for the target environment**; otherwise the suite can pass without actually validating real market behaviour. The only explicit "invalid market" test uses a dummy id `invalid-market-000`, not the configured value.

**401 Unauthorized:** If tests fail with `expected: 200/202 but was: 401`, the API is rejecting the auth. Common causes: (1) Backend invalidates the session (e.g. after cancel order or after some operations). (2) Access token expired (short JWT TTL). (3) Refresh cookie not captured at login or not sent on requests (backend may require both Bearer and Cookie). (4) Token/cookie are captured once per test class in @BeforeClass; if the server invalidates the session mid-suite, later tests in that class (and later classes) keep using the same token and get 401. Check backend logs for session/token invalidation; ensure login response includes Set-Cookie: refresh_token and that it is stored and sent.

**Timeouts (Connect/Read):** Connect to uat-frankfurt.pred.app:443 timed out or Read timed out are network or server issues (VPN, firewall, or server slow). They can make runs flaky and are separate from 401.

---

## 1. API tests (FrameworkSmokeTest, HealthCheckTest, LoginTest)

| Test | What it checks |
|------|----------------|
| **configLoads** | Config provides a non-blank base URI (RestAssured + config load; no HTTP call). |
| **healthEndpointResponds** | *(Disabled)* GET /health returns 200 or 204. Enable when a health endpoint exists. |
| **createApiKey_returns200AndKey** | Create API key returns 200 and a non-empty response body. |
| **loginWithInvalidBody_returns4xx** | Login with empty/invalid body returns 4xx, error object with status_code 400, error_code BAD_REQUEST, and a message. |

---

## 2. Auth Flow (AuthFlowTest)

| Test | What it checks |
|------|----------------|
| **setupSuite** (BeforeClass) | Creates/uses API key, gets signature from sig-server, logs in, stores access token, userId, proxy wallet, EOA, and private key in TokenManager. |
| **printAccessTokenForPostman** | *(Utility, not counted as a test.)* Prints the stored access token to stdout for use in Postman; excluded from Slack notification and test count. |
| **verifyTokenStored** | TokenManager has a token, non-blank userId, and non-blank proxy wallet address. |
| **loginWithInvalidSignature_returns4xx** | Login with invalid signature (wrong wallet + short signature) returns 401 with error_code SIGNATURE_LOGIN_FAILED and a message. |

---

## 3. Enable Trading (EnableTradingTest)

| Test | What it checks |
|------|----------------|
| **enableTrading_success** | Prepare enable-trading returns 200 and a transaction hash; sign it via sig-server; execute returns 200. Skips if trading is already enabled or prepare returns 500 (e.g. no contract code). |

---

## 4. Deposit (DepositTest)

| Test | What it checks |
|------|----------------|
| **depositFunds** | If balance is below configured amount: internal deposit returns 2xx and a transaction hash; cashflow/deposit with that hash returns 2xx. Skips if balance is already sufficient. |
| **depositWithInvalidUserId_returnsFailed** | Internal deposit with invalid user ID returns 200 with success=false, message "Failed to get user wallet address", err_code WALLET_FETCH_FAILED, and correct user_id/amount in body. |

---

## 5. Portfolio (PortfolioTest)

| Test | What it checks |
|------|----------------|
| **getBalance_returns200** | GET portfolio balance returns 200, success=true, non-null usdc_balance and position_balance; usdc_balance parses to a positive number. |
| **getPositions_returns200** | GET portfolio positions returns 200, success=true, and non-null total_invested, total_to_win, position_league_summary, positions. |
| **getBalanceByMarket_returns200** | GET balance by market ID returns 200, success=true, non-null usdc_balance and position_balance; usdc_balance > 0. |
| **getEarnings_returns200** | GET earnings returns 200; body has user_id, realized_pnl, unrealized_pnl, total_pnl. |
| **getBalance_withInvalidToken_returns401** | GET balance with invalid token returns 401 and body message "Unauthorized". |
| **getPositions_withInvalidToken_returns401** | GET positions with invalid token returns 401 and body message "Unauthorized". |
| **getEarnings_withInvalidToken_returns401** | GET earnings with invalid token returns 401 and body message "Unauthorized". |

---

## 6. Order (OrderTest)

| Test | What it checks |
|------|----------------|
| **cancelOrder_withInvalidOrderId_returns4xx** | Cancel with non-existent order ID returns status 4xx (e.g. 404) and body contains error or message. |
| **cancelOrder_withValidOrderId_returns2xx** | Place an order, then cancel it by order_id and market_id; cancel returns 2xx with status user_cancelled, matching order_id, and success message. |
| **flow_placeTwoOrders_cancelSecond_withBalancePnlPositionsChecks** | Place two orders (different signatures), cancel the second; before/after each step, GET balance, earnings, and positions return 200 and expected structure. |
| **placeOrder_balanceBeforeAndAfterReflectsOrder** | Get balance (overall + by market), place one order, get balance again; place-order returns 202; by-market usdc_balance decreases; when reserved/available exist, reserved increases or available decreases. |
| **placeOrder_withInvalidMarketId_returns4xx** | Place order with market_id "invalid-market-000" and valid signature; response status is 4xx (e.g. 404); body has error. |
| **placeOrder_withInvalidSignature_returns4xx** | Place order with dummy signature (0x000000) and invalid token_id; response is 4xx with error body mentioning signature or validation. |
| **placeOrder_withNegativePrice_returns4xx** | Place order with price/amount -1 and dummy signature (no sig-server); response is 4xx with error (e.g. price must be greater than 0). |
| **placeOrder_withValidSignature_returns202** | Sign order via sig-server (long, price=30, qty=100, amount=30.00), place order; response is 202 with status open_order, order_id, "Order placed successfully", filled_quantity "0". |
| **placeOrder_withZeroQuantity_returns4xx** | Place order with quantity 0 (valid signature); response is 4xx with error in body (e.g. missing required fields: quantity). |
| **balance_availableEqualsTotalMinusReserved_whenFieldsPresent** | Overall and by-market balance return 200; overall usdc_balance >= by-market; when total/available/reserved present, available = total - reserved. |

---

## 7. Order Validation (OrderValidationTest)

| Test | What it checks |
|------|----------------|
| **placeOrder_shortSide_returns202** | Place a short order (price=70, qty=100, amount=(100-70)*100/100 = 30.00 USDC); response is 202 with status open_order; then cancel the order. Uses price=70 to avoid self-match with long orders at price=30. |

---

## 8. Balance Service (BalanceServiceTest)

| Test | What it checks |
|------|----------------|
| **balance_decreaseIsConsistentWithOrderSize** | Place one long order (price=30, qty=100); balance decreases by a positive delta; delta is consistent with order size (and reasonable); then cancel. |
| **balance_restoresAfterCancellingOrder** | Place one order, note balance; cancel; wait 2s; balance restores to same value (exact equality). |

---

## 9. Balance Integrity (BalanceIntegrityTest)

| Test | What it checks |
|------|----------------|
| **balance_exactDeltaAfterPlacingOrder** | Place one order (price=30, qty=100); balance delta equals expected (price*quantity/100); then cancel. |
| **balance_exactRestoreAfterCancel** | Place one order, note balance; cancel; wait 2s; balance exactly restores. |
| **balance_twoOrdersDeltaIsTwiceSingleDelta** | Place two identical orders (price=30, qty=100); total balance decrease equals twice the single-order delta; cancel both. |
| **balance_largerOrderDeductsMore** | Place small order (30, 100), measure delta, cancel; place large order (30, 200), measure delta; large delta > small delta; cancel large order. |
| **placeOrder_insufficientBalance_returns4xx** | Place an oversized long order (price=90, qty=999999999, amount = price * quantity / 100 e.g. 90 * 999999999 / 100 = 899999999.10); API returns 4xx (e.g. insufficient balance or validation). If 202, order is cancelled. |

---

## Execution order (suite.xml)

1. API tests: FrameworkSmokeTest, HealthCheckTest, LoginTest  
2. Auth Flow: AuthFlowTest  
3. Enable Trading: EnableTradingTest  
4. Deposit: DepositTest  
5. Portfolio: PortfolioTest  
6. Order: OrderTest  
7. Order Validation: OrderValidationTest  
8. Balance Service: BalanceServiceTest  
9. Balance Integrity: BalanceIntegrityTest  

Total: 34 test methods run (1 health check disabled). Reported as 33 tests (printAccessTokenForPostman excluded from count and Slack). Some tests skip when preconditions are not met (e.g. no token, trading already enabled, balance already sufficient).
