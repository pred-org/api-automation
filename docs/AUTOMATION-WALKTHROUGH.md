# API Automation Walkthrough

This document explains how this framework is structured, how tests execute, and what to check when a run is unstable.

## 1) Project structure

- `src/main/java/com/pred/apitests/config/Config.java`
  - Central config reader (`.env`, system props, fallback properties).
- `src/main/java/com/pred/apitests/base/BaseService.java`
  - Shared RestAssured setup, default HTTP timeouts, auth/cookie request builders.
- `src/main/java/com/pred/apitests/service/*`
  - Service object layer (`AuthService`, `OrderService`, `PortfolioService`, etc.).
- `src/main/java/com/pred/apitests/model/*`
  - Request/response DTOs.
- `src/test/java/com/pred/apitests/base/BaseApiTest.java`
  - Shared test base, session refresh hooks and helpers.
- `src/test/java/com/pred/apitests/test/*`
  - Functional test classes.
- `src/test/resources/suite.xml`
  - Canonical suite order for Maven runs.

## 2) Execution model

- Commands reference (suites, auth, tools): [COMMANDS.md](COMMANDS.md)
- Main run command:
  - `mvn clean test`
- Suite source:
  - `pom.xml` uses `src/test/resources/suite.xml`.
- Session model:
  - User 1 comes from `TokenManager`.
  - User 2 comes from `SecondUserContext` (typically `.env.session2`).
- 401 recovery:
  - Use `AuthService.refreshUser1SessionAfter401()` for user 1.
  - Use `AuthService.refreshSecondUserAndStore()` for user 2.

## 3) Test strategy layers

- Transport contract:
  - HTTP status and required fields.
- Schema contract:
  - JSON schema checks for key endpoints.
- Business assertions:
  - Balance deltas, open-order state, position visibility, trade history entries.
- Eventual consistency handling:
  - `PollingUtil` for open orders/positions/orderbook propagation.

## 4) Why skips happen

Skips are expected in UAT and are usually from runtime preconditions, not test code defects:

- Missing setup/session data (`MARKET_ID`, `TOKEN_ID`, user 2 session).
- Optional endpoints not available in that environment.
- No liquidity/open positions for tests that require them.
- Known disabled tests (`@Test(enabled = false)`).

Use surefire output to separate:
- `FAIL` -> assertions violated.
- `SKIP` -> precondition not met.

## 5) Stability checklist before calling a run "good"

- Full suite result is green:
  - `Failures: 0`, `Errors: 0`.
- Skips are explainable by environment/preconditions.
- No recurring 401 loops in logs.
- Cancel/order tests prove cleanup (no stale open orders for the target market).

## 6) Main docs to use

- `documents/TEST-SUITE-MATRIX.md`
  - High-level matrix and skip explanation.
- `docs/CONFIG_AND_TEST_DATA.md`
  - Required config and test data inputs.
- `docs/COMMANDS-AUTOMATION.md`
  - Useful run/debug commands.
- `docs/FRAMEWORK.md`
  - Existing architecture notes.
