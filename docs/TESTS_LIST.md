# All Tests (Current Suite)

Tests run via `mvn test` using `src/test/resources/suite.xml`. Order below matches suite execution.

---

## 1. API tests

| Test method | Description |
|-------------|-------------|
| **FrameworkSmokeTest.configLoads** | ApiConfig provides base URI (no HTTP; framework smoke). |
| **HealthCheckTest.healthEndpointResponds** | GET health/status returns 200 or 204. *(Disabled by default; enable when health endpoint exists.)* |
| **LoginTest.createApiKey_returns200AndKey** | Create API key returns 200 and non-empty key body. |
| **LoginTest.loginWithInvalidBody_returns4xx** | Login with invalid/empty body returns 4xx. |

---

## 2. Auth Flow

*AuthFlowTest runs `setupSuite()` first (create API key, sign login via sig-server, POST login, store token).*

| Test method | Description |
|-------------|-------------|
| **AuthFlowTest.printAccessTokenForPostman** | Print access token for Postman (copy from output). |
| **AuthFlowTest.verifyTokenStored** | Token is stored in TokenManager after login. |
| **AuthFlowTest.loginWithInvalidSignature_returns4xx** | Login with invalid signature returns 4xx. |

---

## 3. Enable Trading

| Test method | Description |
|-------------|-------------|
| **EnableTradingTest.enableTrading_success** | Prepare, sign transactionHash, execute – enable trading for proxy. *(Skipped if API returns "trading is already enabled".)* |

---

## 4. Deposit

| Test method | Description |
|-------------|-------------|
| **DepositTest.depositFunds** | Internal deposit then cashflow/deposit with transaction_hash. *(Skipped if balance already sufficient.)* |
| **DepositTest.depositWithInvalidUserId_returnsFailed** | Internal deposit with invalid userId returns 200 with success: false. |

---

## 5. Portfolio

| Test method | Description |
|-------------|-------------|
| **PortfolioTest.getBalance_returns200** | GET portfolio balance returns 200. |
| **PortfolioTest.getPositions_returns200** | GET portfolio positions returns 200. |
| **PortfolioTest.getBalanceByMarket_returns200** | GET balance by market returns 200. |

---

## 6. Order

| Test method | Description |
|-------------|-------------|
| **OrderTest.balance_availableEqualsTotalMinusReserved_whenFieldsPresent** | When balance API returns total/available/reserved, assert available = total - reserved (overall and by market). |
| **OrderTest.placeOrder_balanceBeforeAndAfterReflectsOrder** | GET balance (overall + by market), place order (202), GET balance again; assert by-market usdc_balance decreases by order amount (and reserved/available when present). |
| **OrderTest.placeOrder_withInvalidSignature_returns4xx** | Place order with invalid signature returns 4xx. |
| **OrderTest.placeOrder_withValidSignature_returns202** | Place order with valid signature (via sig-server) returns 202. |

---

## Summary

| Suite / area   | Class(es)           | Test count | Notes                    |
|----------------|---------------------|------------|---------------------------|
| API tests      | FrameworkSmoke, HealthCheck, LoginTest | 4          | 1 disabled (health)       |
| Auth Flow      | AuthFlowTest        | 3          | Depends on suite setup    |
| Enable Trading | EnableTradingTest   | 1          | May skip if already on    |
| Deposit        | DepositTest         | 2          | 1 may skip if balance ok |
| Portfolio      | PortfolioTest       | 3          | —                        |
| Order          | OrderTest           | 4          | —                        |
| **Total**      | —                   | **17**     | **16 run, 1 disabled**   |

Run full suite: `mvn clean test`. Sig-server must be running (e.g. port 5050) for login and order signing.
