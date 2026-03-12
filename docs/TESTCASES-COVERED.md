# Test cases covered in the framework

All tests are Java/TestNG. Grouped by test class (suite group). Run with `mvn test` or `mvn test -Dtest=<ClassName>`.

**Type** = smoke | negative | contract | integration | flow.  
- **smoke**: basic availability / structure.  
- **negative**: invalid input, 4xx/401.  
- **contract**: response shape, known values, formulas.  
- **integration**: multi-step or cross-API.  
- **flow**: place/cancel/balance/positions sequences; two-user matching.

---

## Framework / API tests

| Class | Test case | Type | Description |
|-------|-----------|------|-------------|
| **FrameworkSmokeTest** | configLoads | smoke | ApiConfig provides base URI |
| **LoginTest** | createApiKey_returns200AndKey | smoke | Create API key returns 200 and non-empty key body |
| **LoginTest** | loginWithInvalidBody_returns4xx | negative | Login with invalid body returns 4xx |

---

## Auth Flow (user 1 + platform health)

| Class | Test case | Type | Description |
|-------|-----------|------|-------------|
| **AuthFlowTest** | (BeforeClass) | - | Login with signature, store token in TokenManager, write .env.session |
| **AuthFlowTest** | printAccessTokenForPostman | smoke | Print access token for Postman (copy from output) |
| **AuthFlowTest** | verifyTokenStored | smoke | Token is stored in TokenManager after login |
| **AuthFlowTest** | loginWithInvalidSignature_returns4xx | negative | Login with invalid signature returns 4xx |
| **HealthCheckTest** | maintainance_downtimeFalse | smoke | GET /user/maintainance -> downtime == false |
| **HealthCheckTest** | userMe_statusActive_enabledTrading | smoke | GET /user/me -> status == ACTIVE and is_enabled_trading == true |

---

## Enable Trading

| Class | Test case | Type | Description |
|-------|-----------|------|-------------|
| **EnableTradingTest** | enableTrading_success | integration | Prepare then sign transactionHash then execute - enable trading for proxy. One-time setup - skipped if precondition met. |

---

## Deposit

| Class | Test case | Type | Description |
|-------|-----------|------|-------------|
| **DepositTest** | depositFunds | integration | Deposit: internal deposit then cashflow/deposit with transaction_hash. One-time setup - skipped if balance already sufficient. |
| **DepositTest** | depositWithInvalidUserId_returnsFailed | negative | Internal deposit with invalid userId returns 200 with success: false |

---

## Portfolio

| Class | Test case | Type | Description |
|-------|-----------|------|-------------|
| **PortfolioTest** | getBalance_returns200 | smoke | GET portfolio balance returns 200; usdc_balance valid number > 0 |
| **PortfolioTest** | getPositions_returns200 | smoke | GET portfolio positions returns 200; when positions exist, validate entry has side, quantity, average_price, amount |
| **PortfolioTest** | getBalanceByMarket_returns200 | smoke | GET balance by market returns 200; usdc_balance and position_balance valid numbers |
| **PortfolioTest** | getEarnings_returns200 | contract | GET portfolio earnings; PnL fields present and total_pnl == realized_pnl + unrealized_pnl |
| **PortfolioTest** | getEarnings_totalPnlEqualsRealizedPlusUnrealized | contract | Earnings API: total_pnl equals realized_pnl + unrealized_pnl (integrity check) |
| **PortfolioTest** | getBalance_withInvalidToken_returns401 | negative | GET balance with invalid token returns 401 |
| **PortfolioTest** | getPositions_withInvalidToken_returns401 | negative | GET positions with invalid token returns 401 |
| **PortfolioTest** | getEarnings_withInvalidToken_returns401 | negative | GET earnings with invalid token returns 401 |
| **PortfolioTest** | getTradeHistory_returns200_andHasStructure | smoke | GET trade-history returns 200 and response has list structure |
| **PortfolioTest** | getBalance_globalVsScoped_positionBalanceContract | contract | Global vs scoped balance; position_balance parseable (documents API quirk) |
| **PortfolioTest** | getTradeHistory_filteredByMarketId_isSubsetOfGlobal | contract | Scoped trade-history count <= global; every scoped entry has market_id == requested market |
| **PortfolioTest** | getTradeHistory_activityTypes_areKnownValues | contract | Every trade-history entry has activity in [Open Long, Open Short, Redeemed] |
| **PortfolioTest** | getPositions_fieldsAreValid_whenPositionsExist | contract | When positions exist: market_id, side long|short, quantity > 0, average_price 1-99, amount > 0 |

---

## Order (place order happy path + negative/validation)

| Class | Test case | Type | Description |
|-------|-----------|------|-------------|
| **OrderTest** | placeOrder_withValidSignature_returns202 | smoke | Place order with valid signature returns 202; then GET open-orders and assert order appears |
| **OrderTest** | placeOrder_shortSide_returns202 | smoke | Place order with side short returns 202; cleanup cancel |
| **OrderTest** | placeOrder_withInvalidSignature_returns4xx | negative | Place order with invalid signature returns 4xx |
| **OrderTest** | placeOrder_withZeroQuantity_returns4xx | negative | Place order with zero quantity returns 4xx |
| **OrderTest** | placeOrder_withNegativePrice_returns4xx | negative | Place order with negative price returns 4xx |
| **OrderTest** | placeOrder_withInvalidMarketId_returns4xx | negative | Place order with invalid market id returns 4xx |

---

## Orderbook

| Class | Test case | Type | Description |
|-------|-----------|------|-------------|
| **OrderbookTest** | getOrderbook_returns200_structureAndSpread | smoke | GET orderbook returns 200; bids, asks, metadata.spread present (public, no auth) |
| **OrderbookTest** | orderbook_bidSideExists_beforeShortOrder | contract | Guard: bids[] not empty and bids[0].quantity > 0; no bid liquidity = SHORT tests cannot run |
| **OrderbookTest** | orderbook_totalBidQuantity_decreasesAfterShortOrder | integration | total_bid_quantity decreases by N after placing SHORT (limit at best bid) for N shares |

---

## Cancel Order

| Class | Test case | Type | Description |
|-------|-----------|------|-------------|
| **CancelOrderTest** | cancelOrder_withValidOrderId_returns2xx | smoke | Cancel order with valid order_id and market_id returns 2xx |
| **CancelOrderTest** | cancelOrder_withInvalidOrderId_returns4xx | negative | Cancel order with non-existent order id returns 4xx |
| **CancelOrderTest** | cancelOrder_limitOrder_doesNotAppearInTradeHistory | contract | Cancelled unmatched limit order does not appear in trade-history |
| **CancelOrderTest** | cancelOrder_limitOrder_balanceFullyRestored | contract | After cancelling limit order, usdc_balance restored exactly (BigDecimal equality) |

---

## Order Flow (multi-step integration)

| Class | Test case | Type | Description |
|-------|-----------|------|-------------|
| **OrderFlowTest** | flow_placeTwoOrders_cancelSecond_withBalancePnlPositionsChecks | flow | Place 2 orders, cancel 2nd; check balance, earnings, positions, trade-history in between |
| **OrderFlowTest** | flow_twoUsers_placeLongAndShort_mayMatch_positions | flow | **Integration - requires 2 user sessions.** User 1 has LONG bids (liquidity); User 2 places SHORT at 30c -> match. Assert User 2: open-orders empty, SHORT position, Open Short in trade-history, balance decreased by correct amount. |
| **OrderFlowTest** | flow_placeShort_assertPositionAndTradeHistory | flow | Place SHORT at best bid when external liquidity exists; assert position and trade-history. Skips when all bid liquidity belongs to this account (self-match prevention) or no bid liquidity. |
| **OrderFlowTest** | flow_unrealizedPnl_matchesFormula | contract | Unrealized PnL formula (avg_price - mark_price) * quantity / 100 for SHORT; total_pnl == realized + unrealized |

---

## Balance Service (balance behaviour triggered by order actions)

| Class | Test case | Type | Description |
|-------|-----------|------|-------------|
| **BalanceServiceTest** | balance_restoresAfterCancellingOrder | integration | Balance restores after cancelling order; exact equality with 2s wait |
| **BalanceServiceTest** | balance_decreaseIsConsistentWithOrderSize | integration | Discover exact delta for price=30 qty=100; assert positive and reasonable |
| **BalanceServiceTest** | placeOrder_balanceBeforeAndAfterReflectsOrder | integration | Place order; balance before/after; assert balance reflects open order (reserved/available or usdc delta) |
| **BalanceServiceTest** | balance_availableEqualsTotalMinusReserved_whenFieldsPresent | contract | Overall usdc_balance >= by-market; when total/available/reserved present, available = total - reserved |

---

## Balance Integrity

| Class | Test case | Type | Description |
|-------|-----------|------|-------------|
| **BalanceIntegrityTest** | balance_exactDeltaAfterPlacingOrder | integration | Exact delta after placing order (price=30 qty=100) |
| **BalanceIntegrityTest** | balance_exactRestoreAfterCancel | integration | Balance exactly restores after cancel with 2s wait |
| **BalanceIntegrityTest** | balance_twoOrdersDeltaIsTwiceSingleDelta | integration | Two orders of same size: deltas equal |
| **BalanceIntegrityTest** | balance_largerOrderDeductsMore | integration | Larger order deducts more than smaller |
| **BalanceIntegrityTest** | placeOrder_insufficientBalance_returns4xx | negative | Oversized order rejected with 4xx or accepted then cancelled |

---

## Second user (not in default suite)

| Class | Test case | Type | Description |
|-------|-----------|------|-------------|
| **AuthFlowTestUser2** | (BeforeClass) | - | Login as second user, write .env.session2 |
| **AuthFlowTestUser2** | secondUserSessionIsLoadable | smoke | Second user session is loadable |

---

## Summary count

| Group | Test class | Number of test methods |
|-------|------------|------------------------|
| API tests | FrameworkSmokeTest, LoginTest | 3 |
| Auth Flow | AuthFlowTest, HealthCheckTest | 5 |
| Enable Trading | EnableTradingTest | 1 |
| Deposit | DepositTest | 2 |
| Portfolio | PortfolioTest | 13 |
| Order | OrderTest | 6 |
| Orderbook | OrderbookTest | 3 |
| Cancel Order | CancelOrderTest | 4 |
| Order Flow | OrderFlowTest | 4 |
| Balance Service | BalanceServiceTest | 4 |
| Balance Integrity | BalanceIntegrityTest | 5 |
| Second user | AuthFlowTestUser2 | 1 |
| **Total (default suite)** | | **50** |

(AuthFlowTestUser2 is run explicitly, not in default suite.)
