# Test cases covered in the framework

All tests are Java/TestNG. Run with `mvn test` (full suite) or `mvn test -Dtest=<ClassName>`.

**Gaps and roadmap:** See [docs/GAPS-AND-ROADMAP.md](GAPS-AND-ROADMAP.md) for DB validation, rate limit, boundary tests, and other not-yet-covered items.

**Type** = smoke | negative | contract | integration | flow  
- **smoke**: availability, structure, happy path.  
- **negative**: invalid input, 4xx/401.  
- **contract**: response shape, formulas, known values.  
- **integration**: multi-step or cross-API.  
- **flow**: place/cancel/balance/positions sequences; two-user matching.

---

## 1. API tests (Framework + Login)

| Class | Test method | Type | What we're testing |
|-------|-------------|------|---------------------|
| FrameworkSmokeTest | configLoads | smoke | Config provides base URI (framework loads). |
| LoginTest | createApiKey_returns200AndKey | smoke | Create API key returns 200 and non-empty key body. |
| LoginTest | loginWithInvalidBody_returns4xx | negative | Login with invalid body returns 4xx. |

---

## 2. Auth Flow (user 1 + platform health)

| Class | Test method | Type | What we're testing |
|-------|-------------|------|---------------------|
| AuthFlowTest | (BeforeClass) | - | Login with signature; store token in TokenManager; write .env.session. |
| AuthFlowTest | printAccessTokenForPostman | smoke | Access token is available for copy. |
| AuthFlowTest | verifyTokenStored | smoke | Token is stored in TokenManager after login. |
| AuthFlowTest | refreshToken_returns200_andNewToken | smoke | POST /auth/refresh/token returns 200 and new access_token different from old. Skips on 404. |
| AuthFlowTest | loginWithInvalidSignature_returns4xx | negative | Login with invalid signature returns 4xx. |
| HealthCheckTest | maintainance_downtimeFalse | smoke | GET /user/maintainance returns downtime == false. |
| HealthCheckTest | userMe_statusActive_enabledTrading | smoke | GET /user/me returns status == ACTIVE and is_enabled_trading == true. |

---

## 3. Enable Trading (user 1)

| Class | Test method | Type | What we're testing |
|-------|-------------|------|---------------------|
| EnableTradingTest | enableTrading_success | integration | Prepare -> sign transactionHash -> execute; enable trading for proxy. Skipped if already enabled or 401. |

---

## 4. Auth Flow User 2

| Class | Test method | Type | What we're testing |
|-------|-------------|------|---------------------|
| AuthFlowTestUser2 | (BeforeClass) | - | Login as user 2 (API_KEY_2); write .env.session2. Skips on 401 if backend allows only one user per key. |
| AuthFlowTestUser2 | secondUserSessionIsLoadable | smoke | Second user session is loadable from .env.session2 / SecondUserContext. |

---

## 5. Enable Trading User 2

| Class | Test method | Type | What we're testing |
|-------|-------------|------|---------------------|
| EnableTradingTestUser2 | enableTrading_user2_success | integration | Enable trading for user 2 proxy (prepare/sign/execute with user 2 session). |

---

## 6. Deposit

| Class | Test method | Type | What we're testing |
|-------|-------------|------|---------------------|
| DepositTest | depositFunds | integration | Internal deposit then cashflow/deposit with transaction_hash. Skipped if balance already sufficient. |
| DepositTest | depositWithInvalidUserId_returnsFailed | negative | Internal deposit with invalid userId returns 200 with success: false. |

---

## 7. Portfolio

| Class | Test method | Type | What we're testing |
|-------|-------------|------|---------------------|
| PortfolioTest | getBalance_returns200 | smoke | GET portfolio balance returns 200; usdc_balance valid. |
| PortfolioTest | getPositions_returns200 | smoke | GET portfolio positions returns 200; entries have side, quantity, average_price, amount when present. |
| PortfolioTest | getBalanceByMarket_returns200 | smoke | GET balance by market returns 200; usdc_balance and position_balance valid (position_balance can be negative for short). |
| PortfolioTest | getEarnings_returns200 | contract | GET earnings returns 200; PnL fields present; total_pnl == realized_pnl + unrealized_pnl. |
| PortfolioTest | getEarnings_totalPnlEqualsRealizedPlusUnrealized | contract | total_pnl equals realized_pnl + unrealized_pnl (integrity). |
| PortfolioTest | getBalance_withInvalidToken_returns401 | negative | GET balance with invalid token returns 401. |
| PortfolioTest | getPositions_withInvalidToken_returns401 | negative | GET positions with invalid token returns 401. |
| PortfolioTest | getEarnings_withInvalidToken_returns401 | negative | GET earnings with invalid token returns 401. |
| PortfolioTest | getTradeHistory_returns200_andHasStructure | smoke | GET trade-history returns 200 and has list structure. |
| PortfolioTest | getBalance_globalVsScoped_positionBalanceContract | contract | Global vs scoped balance; position_balance contract. |
| PortfolioTest | getTradeHistory_filteredByMarketId_isSubsetOfGlobal | contract | Scoped trade-history count <= global; every entry has market_id == requested market. |
| PortfolioTest | getTradeHistory_activityTypes_areKnownValues | contract | Every trade-history entry has activity in [Open Long, Open Short, Redeemed]. |
| PortfolioTest | getPositions_fieldsAreValid_whenPositionsExist | contract | When positions exist: market_id, side long|short, quantity > 0, average_price 1-99, amount > 0. |

---

## 8. Order (place order: user 1, then user 2 same tests)

| Class | Test method | Type | What we're testing |
|-------|-------------|------|---------------------|
| OrderTest | placeOrder_withValidSignature_returns202 | smoke | Place order with valid signature returns 202; order appears in open-orders. |
| OrderTest | placeOrder_shortSide_returns202 | smoke | Place order side=short returns 202; cleanup cancel. |
| OrderTest | placeOrder_withInvalidSignature_returns4xx | negative | Place order with invalid signature returns 4xx. |
| OrderTest | placeOrder_withZeroQuantity_returns4xx | negative | Place order with zero quantity returns 4xx. |
| OrderTest | placeOrder_withNegativePrice_returns4xx | negative | Place order with negative price returns 4xx. |
| OrderTest | placeOrder_withInvalidMarketId_returns4xx | negative | Place order with invalid market id returns 4xx. |
| OrderTest | cancelOrder_withInvalidOrderId_returns4xx | negative | Cancel with non-existent order id returns 4xx. |
| OrderTestUser2 | (same methods as OrderTest) | smoke/negative | Same tests as OrderTest but run as user 2 (SecondUserContext). |

---

## 9. Orderbook

| Class | Test method | Type | What we're testing |
|-------|-------------|------|---------------------|
| OrderbookTest | getOrderbook_returns200_structureAndSpread | smoke | GET orderbook returns 200; structure has bids, asks, metadata; metadata.spread present (public, no auth). |
| OrderbookTest | orderbook_bidSideExists_beforeShortOrder | contract | Bids exist and bids[0].quantity > 0; guard for SHORT tests. |
| OrderbookTest | orderbook_totalBidQuantity_decreasesAfterShortOrder | integration | total_bid_quantity decreases by N after placing SHORT for N shares. |

---

## 10. Market Discovery

| Class | Test method | Type | What we're testing |
|-------|-------------|------|---------------------|
| MarketDiscoveryTest | getLeagues_returns200_andList | smoke | GET /market-discovery/leagues returns 200 and list of leagues (id, name, etc.). Skips on 404. |
| MarketDiscoveryTest | getFixturesByLeague_returns200 | smoke | GET /market-discovery/fixtures?league_id=X returns 200; uses first league from getLeagues. Skips on 404. |

---

## 11. Cancel Order (user 1, then user 2 same tests)

| Class | Test method | Type | What we're testing |
|-------|-------------|------|---------------------|
| CancelOrderTest | cancelOrder_withValidOrderId_returns2xx | smoke | Cancel with valid order_id and market_id returns 2xx; status user_cancelled. |
| CancelOrderTest | cancelOrder_withInvalidOrderId_returns4xx | negative | Cancel with non-existent order id returns 4xx. |
| CancelOrderTest | cancelOrder_limitOrder_doesNotAppearInTradeHistory | contract | Cancelled unmatched limit order does not appear in trade-history. |
| CancelOrderTest | cancelOrder_limitOrder_balanceFullyRestored | contract | After cancelling limit order, usdc_balance restored exactly (to the cent). |
| CancelOrderTestUser2 | (same methods as CancelOrderTest) | smoke/negative/contract | Same tests as CancelOrderTest but run as user 2. |

---

## 12. Order Flow (multi-step; user 1 + two-user)

| Class | Test method | Type | What we're testing |
|-------|-------------|------|---------------------|
| OrderFlowTest | flow_placeTwoOrders_cancelSecond_withBalancePnlPositionsChecks | flow | Place 2 orders, cancel 2nd; check balance, earnings, positions, trade-history in between. |
| OrderFlowTest | flow_twoUsers_placeLongAndShort_mayMatch_positions | flow | User 1 LONG bids; User 2 places SHORT at same price; match. Assert User 2: open-orders empty, SHORT position, Open Short in trade-history, balance decreased. |
| OrderFlowTest | flow_placeShort_assertPositionAndTradeHistory | flow | Place SHORT at best bid when external liquidity exists; assert position and trade-history (polls for eventual consistency). Skips on 422 "No external liquidity". |
| OrderFlowTest | flow_unrealizedPnl_matchesFormula | contract | Unrealized PnL: (avg_price - mark_price) * quantity / 100 for SHORT; total_pnl == realized + unrealized. |

---

## 13. Balance Service

| Class | Test method | Type | What we're testing |
|-------|-------------|------|---------------------|
| BalanceServiceTest | balance_restoresAfterCancellingOrder | integration | Balance restores after cancelling order; exact equality with 2s wait. |
| BalanceServiceTest | balance_decreaseIsConsistentWithOrderSize | integration | Delta for price=30 qty=100 is positive and reasonable. |
| BalanceServiceTest | placeOrder_balanceBeforeAndAfterReflectsOrder | integration | Place order; balance before/after reflects open order. |
| BalanceServiceTest | balance_availableEqualsTotalMinusReserved_whenFieldsPresent | contract | When total/available/reserved present, available = total - reserved. |

---

## 14. Balance Integrity

| Class | Test method | Type | What we're testing |
|-------|-------------|------|---------------------|
| BalanceIntegrityTest | balance_exactDeltaAfterPlacingOrder | integration | Exact balance delta after placing order (price=30 qty=100). |
| BalanceIntegrityTest | balance_exactRestoreAfterCancel | integration | Balance exactly restores after cancel with 2s wait. |
| BalanceIntegrityTest | balance_twoOrdersDeltaIsTwiceSingleDelta | integration | Two orders same size: combined delta equals twice single order delta. |
| BalanceIntegrityTest | balance_largerOrderDeductsMore | integration | Larger order deducts more than smaller. |
| BalanceIntegrityTest | placeOrder_insufficientBalance_returns4xx | negative | Oversized order rejected with 4xx or accepted then cancelled. |

---

## Summary

| Suite group | Classes | Test methods (approx) |
|-------------|---------|------------------------|
| API tests | FrameworkSmokeTest, LoginTest | 3 |
| Auth Flow | AuthFlowTest, HealthCheckTest | 5 |
| Enable Trading | EnableTradingTest | 1 |
| Auth Flow User 2 | AuthFlowTestUser2 | 1 |
| Enable Trading User 2 | EnableTradingTestUser2 | 1 |
| Deposit | DepositTest | 2 |
| Portfolio | PortfolioTest | 13 |
| Order | OrderTest, OrderTestUser2 | 7 + 7 (same names, user 2) |
| Orderbook | OrderbookTest | 3 |
| Market Discovery | MarketDiscoveryTest | 2 |
| Cancel Order | CancelOrderTest, CancelOrderTestUser2 | 4 + 4 (same names, user 2) |
| Order Flow | OrderFlowTest | 4 |
| Balance Service | BalanceServiceTest | 4 |
| Balance Integrity | BalanceIntegrityTest | 5 |
| **Total** | | **~66** (depending on skips) |

**Response validation:** Place-order and cancel-order responses are validated against JSON schemas in `src/test/resources/schemas/`. After place order (202), open-orders is polled for up to 3s for eventual consistency (Kafka lag).

OrderTestUser2 and CancelOrderTestUser2 run the same test methods as OrderTest and CancelOrderTest but with user 2's session (SecondUserContext), so place and cancel are exercised for both users in one suite run.

---

## Appendix: Suite run diagram (reference)

Historical snapshot of one full suite run (status symbols may differ per run). Migrated from the former `flow.md`.

```
Suite Start
│
├── AUTH BLOCK (sequential)
│   ├── [ok] configLoads
│   ├── [ok] createApiKey_returns200AndKey
│   ├── [ok] loginWithInvalidBody_returns4xx
│   ├── [ok] loginWithInvalidSignature_returns4xx
│   ├── [ok] printAccessTokenForPostman
│   ├── [ok] refreshToken_returns200_andNewToken
│   ├── [ok] verifyTokenStored
│   ├── [ok] maintainance_downtimeFalse
│   ├── [ok] userMe_statusActive_enabledTrading
│   └── [skip] enableTrading_success  (SKIP: trading already enabled both users)
│
├── DEPOSIT BLOCK (sequential)
│   ├── [skip] depositFunds (User1)  (SKIP: balance sufficient, no deposit needed)
│   ├── [ok] depositWithInvalidUserId_returnsFailed
│   ├── [skip] depositFunds (User2)  (SKIP: balance sufficient)
│   └── [ok] depositWithInvalidUserId_returnsFailed
│
├── PORTFOLIO BLOCK (parallel methods)
│   ├── [ok] balance_globalVsScopedByMarket
│   ├── [ok] earnings_totalPnl_equalsRealizedPlusUnrealized
│   ├── [ok] getBalanceByMarket_returns200
│   ├── [ok] getBalance_returns200
│   ├── [ok] getBalance_withInvalidToken_returns401
│   ├── [ok] getEarnings_returns200
│   ├── [ok] getEarnings_withInvalidToken_returns401
│   ├── [ok] getPositions_returns200
│   ├── [ok] getPositions_withInvalidToken_returns401
│   ├── [ok] positions_fieldsAreValid
│   ├── [ok] tradeHistory_activityTypes_areValid
│   └── [ok] tradeHistory_returnsValidStructure
│
├── ORDERBOOK BLOCK (parallel)
│   ├── [ok] orderbook_returnsValidStructure
│   ├── [ok] orderbook_hasBidsBeforeShortOrder
│   └── [skip] orderbook_bidQuantityDecreasesAfterShort  (SKIP: needs live bids)
│
├── MARKET DISCOVERY BLOCK (parallel)
│   ├── [ok] getLeagues_returns200_andList
│   └── [skip] getFixturesByLeague_returns200  (SKIP: example run server-side bug on league filter)
│
├── ORDER BLOCK - User1 (sequential)
│   ├── [ok] cancelOrder_invalidOrderId_rejected
│   ├── [ok] placeOrder_invalidMarketId_rejected
│   ├── [ok] placeOrder_invalidSignature_rejected
│   ├── [ok] placeOrder_negativePrice_rejected
│   ├── [ok] placeOrder_shortSide_accepted (User2 provides LONG liquidity first)
│   ├── [ok] placeOrder_validSignature_accepted (LONG placed + cancelled)
│   └── [ok] placeOrder_zeroQuantity_rejected
│
├── ORDER BLOCK - User2 (sequential, overlaps with User1)
│   ├── [ok] cancelOrder_invalidOrderId_rejected
│   ├── [ok] placeOrder_invalidMarketId_rejected
│   ├── [ok] placeOrder_invalidSignature_rejected
│   ├── [ok] placeOrder_negativePrice_rejected
│   ├── [ok] placeOrder_shortSide_accepted
│   ├── [skip] placeOrder_validSignature_accepted  (SKIP: transient 401 from UAT)
│   └── [ok] placeOrder_zeroQuantity_rejected
│
├── CANCEL ORDER BLOCK (sequential)
│   ├── [fail] cancelLimitOrder_balanceFullyRestored  (FAIL: transient 401 on balance GET)
│   ├── [ok] cancelLimitOrder_notInTradeHistory
│   ├── [ok] cancelOrder_invalidOrderId_rejected
│   └── [ok] cancelOrder_validOrderId_accepted
│   (+ User2 versions of above, all pass)
│
├── ORDER FLOW BLOCK (sequential)
│   ├── [ok] twoOrders_cancelSecond_balanceAndPnlCorrect
│   ├── [ok] twoUser_longShortMatch_assertsPosition
│   ├── [ok] twoUser_matchedShort_assertsPositionAndHistory
│   └── [skip] positions_unrealizedPnl_matchesFormula  (SKIP: User1 has no SHORT position)
│
├── CLOSE POSITION BLOCK (sequential)
│   └── [skip] closePosition_balanceAndPnlAsserted  (SKIP: position poll timeout)
│
└── BALANCE INTEGRITY BLOCK (sequential)
    ├── [ok] balance_availableEqualsTotalMinusReserved
    ├── [ok] cancelOrder_balanceRestores
    ├── [ok] placeOrder_balanceDecreasesCorrectly
    ├── [ok] placeOrder_balanceReflectsOpenOrder
    ├── [ok] balance_largerOrderDeductsMore
    ├── [ok] cancelOrder_balanceRestoresExactly
    ├── [ok] placeOrder_balanceDecreasesExactly
    ├── [ok] placeOrder_insufficientBalance_returns4xx
    └── [ok] twoOrders_balanceDeductionIsConsistent
```

See `src/test/resources/suite.xml` for the authoritative class order.
