# Test coverage gaps and roadmap

This document lists known gaps and a suggested priority order. The framework already covers: happy path (place, cancel, balance, positions, earnings), negative tests (invalid token/signature/market/quantity), contract tests (PnL formula, balance, trade-history structure), flow tests (two-user matching, cancel restores balance), and both users.

---

## Addressed (recently)

| Gap | Fix |
|-----|-----|
| JSON schema / response shape | Place-order and cancel-order responses validated against JSON schemas in `src/test/resources/schemas/`. Uses RestAssured json-schema-validator. |
| Eventual consistency / Kafka lag | After place order (202), open-orders is polled for up to 3s for the order_id to appear. Position polling already added for OrderFlowTest. |
| Token refresh | `AuthFlowTest.refreshToken_returns200_andNewToken` calls POST /auth/refresh/token, asserts 200 and new access_token different from old. Skips on 404 if endpoint not implemented. |
| Market discovery | `MarketDiscoveryTest`: getLeagues_returns200_andList, getFixturesByLeague_returns200. Suite group "Market Discovery" added. |

---

## Not yet covered

### 1. DB validation (high value, needs backend)

- **Gap:** No DB-level checks. Tests only assert API responses, not persisted state.
- **Examples:** Place order 202 but is the order actually stored? Cancel 2xx but is status updated to cancelled? Balance restore reflected in ledger?
- **Options:** Direct DB queries (if test env has DB access), or verify via a separate read API that proves persistence. Discuss with backend team for DB access or a dedicated “consistency” API.

### 2. Rate limit (429)

- **Gap:** No TestNG test for rate limiting. k6 covers this; TestNG does not.
- **Idea:** Send N rapid requests (e.g. 50), assert at least one 429 or that response indicates rate limit. Depends on backend threshold.

### 3. GET /competitions/status

- **Gap:** No test for this endpoint.
- **Idea:** Add a smoke test: GET /competitions/status, assert 200 and expected structure if documented.

### 4. Boundary tests (price/quantity)

- **Gap:** No boundary tests for price (0, 1, 99, 100) or quantity.
- **Idea:** Add tests for price at 0, 1, 99, 100 (expect 4xx for invalid or 202 for valid per API spec); same for quantity min/max.

### 5. Concurrent orders (same user)

- **Gap:** No test for concurrent place/cancel from same user (race conditions).
- **Idea:** Multi-threaded test: same user places/cancels in parallel; assert no 5xx and final state consistent (e.g. balance, open-orders).

### 6. Strict schema (no extra fields)

- **Gap:** Current JSON schemas use `additionalProperties: true`. No test fails if the API adds unexpected fields.
- **Idea:** Optional strict schema (e.g. `additionalProperties: false`) for a subset of endpoints to catch unintended new fields; use in a separate test or profile to avoid blocking forward evolution.

---

## Priority order (suggested)

1. **DB validation** – Discuss with backend; add once access or consistency API is available.
2. **Rate limit test** – Add one TestNG test that triggers 429 (or equivalent) and asserts behaviour.
3. **GET /competitions/status** – Add when endpoint is stable and documented.
4. **Boundary tests** – Add price/quantity boundary cases per API spec.
5. **Concurrent orders** – Add when needed for risk/consistency coverage.
6. **Strict schema** – Optional; add for critical responses if schema drift is a concern.
