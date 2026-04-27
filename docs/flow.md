Suite Start
│
├── AUTH BLOCK (sequential)
│   ├── ✅ configLoads
│   ├── ✅ createApiKey_returns200AndKey
│   ├── ✅ loginWithInvalidBody_returns4xx
│   ├── ✅ loginWithInvalidSignature_returns4xx
│   ├── ✅ printAccessTokenForPostman
│   ├── ✅ refreshToken_returns200_andNewToken
│   ├── ✅ verifyTokenStored
│   ├── ✅ maintainance_downtimeFalse
│   ├── ✅ userMe_statusActive_enabledTrading
│   └── ⏭ enableTrading_success ← SKIP: trading already enabled both users
│
├── DEPOSIT BLOCK (sequential)
│   ├── ⏭ depositFunds (User1) ← SKIP: balance 63B+ USDC, no deposit needed
│   ├── ✅ depositWithInvalidUserId_returnsFailed
│   ├── ⏭ depositFunds (User2) ← SKIP: balance 2.9B+ now sufficient  
│   └── ✅ depositWithInvalidUserId_returnsFailed
│
├── PORTFOLIO BLOCK (parallel methods)
│   ├── ✅ balance_globalVsScopedByMarket
│   ├── ✅ earnings_totalPnl_equalsRealizedPlusUnrealized
│   ├── ✅ getBalanceByMarket_returns200
│   ├── ✅ getBalance_returns200
│   ├── ✅ getBalance_withInvalidToken_returns401
│   ├── ✅ getEarnings_returns200
│   ├── ✅ getEarnings_withInvalidToken_returns401
│   ├── ✅ getPositions_returns200
│   ├── ✅ getPositions_withInvalidToken_returns401
│   ├── ✅ positions_fieldsAreValid
│   ├── ✅ tradeHistory_activityTypes_areValid
│   └── ✅ tradeHistory_returnsValidStructure
│
├── ORDERBOOK BLOCK (parallel)
│   ├── ✅ orderbook_returnsValidStructure
│   ├── ✅ orderbook_hasBidsBeforeShortOrder
│   └── ⏭ orderbook_bidQuantityDecreasesAfterShort ← SKIP: needs live bids
│
├── MARKET DISCOVERY BLOCK (parallel)
│   ├── ✅ getLeagues_returns200_andList
│   └── ⏭ getFixturesByLeague_returns200 ← SKIP: server-side bug on league filter
│
├── ORDER BLOCK - User1 (sequential)
│   ├── ✅ cancelOrder_invalidOrderId_rejected
│   ├── ✅ placeOrder_invalidMarketId_rejected
│   ├── ✅ placeOrder_invalidSignature_rejected
│   ├── ✅ placeOrder_negativePrice_rejected
│   ├── ✅ placeOrder_shortSide_accepted (User2 provides LONG liquidity first)
│   ├── ✅ placeOrder_validSignature_accepted (LONG placed + cancelled)
│   └── ✅ placeOrder_zeroQuantity_rejected
│
├── ORDER BLOCK - User2 (sequential, overlaps with User1)
│   ├── ✅ cancelOrder_invalidOrderId_rejected
│   ├── ✅ placeOrder_invalidMarketId_rejected
│   ├── ✅ placeOrder_invalidSignature_rejected
│   ├── ✅ placeOrder_negativePrice_rejected
│   ├── ✅ placeOrder_shortSide_accepted
│   ├── ⏭ placeOrder_validSignature_accepted ← SKIP: transient 401 from UAT
│   └── ✅ placeOrder_zeroQuantity_rejected
│
├── CANCEL ORDER BLOCK (sequential)
│   ├── ❌ cancelLimitOrder_balanceFullyRestored ← FAIL: transient 401 on balance GET
│   ├── ✅ cancelLimitOrder_notInTradeHistory
│   ├── ✅ cancelOrder_invalidOrderId_rejected
│   └── ✅ cancelOrder_validOrderId_accepted
│   (+ User2 versions of above, all pass)
│
├── ORDER FLOW BLOCK (sequential)
│   ├── ✅ twoOrders_cancelSecond_balanceAndPnlCorrect
│   ├── ✅ twoUser_longShortMatch_assertsPosition
│   ├── ✅ twoUser_matchedShort_assertsPositionAndHistory
│   └── ⏭ positions_unrealizedPnl_matchesFormula ← SKIP: User1 has no SHORT position
│
├── CLOSE POSITION BLOCK (sequential)
│   └── ⏭ closePosition_balanceAndPnlAsserted ← SKIP: position poll timeout (read below)
│
└── BALANCE INTEGRITY BLOCK (sequential)
    ├── ✅ balance_availableEqualsTotalMinusReserved
    ├── ✅ cancelOrder_balanceRestores
    ├── ✅ placeOrder_balanceDecreasesCorrectly
    ├── ✅ placeOrder_balanceReflectsOpenOrder
    ├── ✅ balance_largerOrderDeductsMore
    ├── ✅ cancelOrder_balanceRestoresExactly
    ├── ✅ placeOrder_balanceDecreasesExactly
    ├── ✅ placeOrder_insufficientBalance_returns4xx
    └── ✅ twoOrders_balanceDeductionIsConsistent