# API Automation – Command reference

Important commands for the Java/TestNG API automation. Run from the project root. Prereqs: Java 17+, Maven, sig-server running (for login and sign-order).

---

## Prereqs and config

| Item | Description |
|------|-------------|
| **Sig-server** | Must be running (e.g. `http://localhost:5050`) for login (sign-create-proxy) and order signing (sign-order). |
| **.env** | Optional. In project root. Sets env for the run (e.g. `API_BASE_URI_PUBLIC`, `PRIVATE_KEY`, `MARKET_ID`). Loaded by Config before tests. |
| **testdata.properties** | `src/test/resources/testdata.properties`. Base URLs, market ID, token ID, wallet (`private.key`, `eoa.address`). Do not commit real secrets; use env or local overrides. |
| **API key** | Leave `api.key` unset in testdata to create a new key each run. Or set `API_KEY` env / `api.key` property if reusing. |

---

## Auth (user 1 and user 2)

| Command | Description |
|---------|-------------|
| `mvn test -Dtest=AuthFlowTest` | Login as **user 1** (wallet from `private.key` / `PRIVATE_KEY`). Stores token in TokenManager and writes `.env.session` in project root. Run this first so other tests have a session. |
| `mvn test -Dtest=AuthFlowTestUser2` | Login as **user 2** (wallet from `PRIVATE_KEY_2` or `second.user.private.key`). Does **not** overwrite TokenManager. Writes `.env.session2`. Run after AuthFlowTest (needs API key from user 1 run or config). |
| **Second user config** | Set second wallet key: env `PRIVATE_KEY_2` or in testdata `second.user.private.key=<key>`. Optional: `EOA_ADDRESS_2` / `second.user.eoa`. |

---

## Run tests

| Command | Description |
|---------|-------------|
| `mvn test` | Run full TestNG suite (`src/test/resources/suite.xml`): Framework smoke, Health, Login, Auth Flow, Enable Trading, Deposit, Portfolio, Order, Order Validation, Balance Service, Balance Integrity. |
| `mvn test -Dtest=OrderTest` | Run only the OrderTest class (place/cancel, balance, flows). |
| `mvn test -Dtest=OrderTest#flow_twoUsers_placeLongAndShort_mayMatch_positions` | Run only the two-user flow test (user 1 long, user 2 short; requires second user session). |
| `mvn test -Dtest=PortfolioTest` | Run only PortfolioTest (balance, positions, earnings). |
| `mvn test -Dtest=AuthFlowTest,AuthFlowTestUser2` | Run both auth tests (user 1 then user 2 login). |
| `mvn test -Dsuite=src/test/resources/suite.xml` | Explicit suite file (default is already this). |

---

## Suite structure (default)

| Test name | Classes | Description |
|-----------|---------|-------------|
| API tests | FrameworkSmokeTest, LoginTest | Framework and login. |
| Auth Flow | AuthFlowTest, HealthCheckTest | User 1 login, write .env.session; then platform health (GET /user/maintainance, GET /user/me per API reference test case 1). |
| Enable Trading | EnableTradingTest | Enable trading for user 1. |
| Deposit | DepositTest | Deposit flow. |
| Portfolio | PortfolioTest | Balance, positions, earnings. |
| Order | OrderTest | Place, cancel, balance, two-user flow. |
| Order Validation | OrderValidationTest | Order validation rules. |
| Balance Service | BalanceServiceTest | Balance service behaviour. |
| Balance Integrity | BalanceIntegrityTest | Balance consistency. |

AuthFlowTestUser2 is not in the default suite; run it explicitly when you need a second user session.

---

## Two-user flow (matches and positions)

| Step / Command | Description |
|----------------|-------------|
| 1. User 1 | Run `mvn test -Dtest=AuthFlowTest` (and set `private.key` / `PRIVATE_KEY` for first wallet). |
| 2. User 2 | Set `PRIVATE_KEY_2` or `second.user.private.key` for second wallet. Run `mvn test -Dtest=AuthFlowTestUser2`. |
| 3. Two-user test | Run `mvn test -Dtest=OrderTest#flow_twoUsers_placeLongAndShort_mayMatch_positions`. User 1 places long, user 2 places short at same price; when they match both get positions. |
| **Session files** | User 1: `.env.session` (written by AuthFlowTest). User 2: `.env.session2` (written by AuthFlowTestUser2). Automation loads user 2 via SecondUserContext from `.env.session2` or `USER_2_*` env. |

---

## Config properties (summary)

| Property / env | Description |
|----------------|-------------|
| `api.base.uri.public` / `API_BASE_URI_PUBLIC` | Public API base (e.g. UAT). |
| `api.base.uri.internal` / `API_BASE_URI_INTERNAL` | Internal API base (e.g. for API key creation). |
| `market.id` / `MARKET_ID` | Market ID for order/balance tests. |
| `token.id` / `TOKEN_ID` | Token ID for place order. |
| `private.key` / `PRIVATE_KEY` | User 1 wallet private key (login + sign-order). |
| `eoa.address` / `EOA_ADDRESS` | User 1 EOA (optional override). |
| `sig.server.url` / `SIG_SERVER_URL` | Sig-server URL (default `http://localhost:5050`). |
| `second.user.private.key` / `PRIVATE_KEY_2` | User 2 wallet private key (for AuthFlowTestUser2). |
| `second.user.eoa` / `EOA_ADDRESS_2` | User 2 EOA (optional). |

---

## Reports and artifacts

| Item | Description |
|------|-------------|
| **Surefire reports** | `target/surefire-reports/` after `mvn test`. |
| **Allure** | `mvn allure:serve` (after run) to open Allure report if Allure is used. |
| **.env.session** | Written by AuthFlowTest; used by k6/shell if you source it. Not used by Java at runtime (TokenManager holds user 1 in memory). |
| **.env.session2** | Written by AuthFlowTestUser2; read by SecondUserContext when running two-user tests. |

---

## Quick reference: run order for two-user

1. `mvn test -Dtest=AuthFlowTest`  
2. Set second wallet key; then `mvn test -Dtest=AuthFlowTestUser2`  
3. `mvn test -Dtest=OrderTest#flow_twoUsers_placeLongAndShort_mayMatch_positions`  
4. Optionally run full suite: `mvn test`
