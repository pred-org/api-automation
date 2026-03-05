# Design Discussion: Enums, POJOs, Utils, Assertions, Database

No code changes here; this doc captures where things live and how they fit together so we stay aligned.

---

## 1. Where and What Do We Save in Enums?

**Where:** In the model layer. Convention: `src/main/java/com/pred/apitests/model/` or a subpackage like `model/constants/` or `model/enums/` (your choice). Enums are part of the domain vocabulary, so they sit with models.

**What to put in enums:** Fixed, closed sets of values that appear in **requests** or **response assertions**. Use enums so we do not scatter string literals and risk typos.

| Enum (example) | Values | Used in |
|----------------|--------|--------|
| **OrderSide** | LONG, SHORT | Place-order request body; position assertions (side) |
| **OrderType** | LIMIT, MARKET | Place-order request body |
| **PositionState** or **MarketState** | OPEN, SETTLED, REDEEMED (or whatever the API returns) | Assertions after order, after resolution, after redeem |
| **ChainType** (if needed) | e.g. BASE, BASE_SEPOLIA | Login request body |

**What not to put in enums:** Open-ended values (e.g. free-form strings), numeric IDs (market id, user id), amounts, timestamps. Those stay as strings/numbers in POJOs or config.

**Summary:** Enums = fixed domain literals for side, type, state. One place for the value set; use in request POJOs and when asserting response fields.

---

## 2. What Do We Save in POJOs?

**Where:** `src/main/java/com/pred/apitests/model/request/` and `model/response/` (per FRAMEWORK.md).

**Request POJOs** — What we send in the body (and optionally query/headers if you want a small DTO).

- **Purpose:** Shape the payload in one place; avoid typos and inconsistent keys; optional Builder for readable, order-independent construction.
- **Examples:** `LoginRequest` (wallet_address, signature, message, nonce, chain_type, timestamp), `DepositRequest` (user_id, amount), `PlaceOrderRequest` (salt, market_id, side, token_id, price, quantity, amount, signature, type, timestamp, expiration, etc.). Side/type can use enums (OrderSide, OrderType).
- **What we save:** Only what the API expects. No internal-only fields; keep request POJOs as a 1:1 mapping to API contract.

**Response POJOs** — What we parse from the server (for type-safe assertions and reuse).

- **Purpose:** Map JSON to Java objects (e.g. Jackson/Gson) so tests can do `assertThat(loginResponse.getAccessToken()).isNotBlank()` instead of raw `response.jsonPath().getString("data.access_token")`. Single place for response shape.
- **Examples:** `LoginResponse` (access_token, user_id, proxy_wallet_address), `BalanceResponse` (success, total_balance, available_balance, reserved_balance, position_balance or whatever the API returns), `PositionsResponse` or `PositionItem` (market_id, side, size, state). State can use an enum when we know the allowed values.
- **What we save:** Only what we need for assertions or for passing data to the next step (e.g. token, user_id, proxy for later calls). Extra API fields can be ignored or omitted from the POJO.

**Optional vs required:** You can start with RestAssured `jsonPath()` and add response POJOs only where type-safe assertions or multi-step data passing justify it. Request POJOs are helpful as soon as payloads get large or repeated.

**Summary:** Request POJOs = outgoing body shape (with Builder optional). Response POJOs = incoming body shape for parsing and asserting. Both stay aligned to API contract; enums used for fixed fields (side, type, state).

---

## 3. What About Util Files?

**Where:** Not spelled out in FRAMEWORK.md. Sensible place: `src/main/java/com/pred/apitests/util/` (or `utils/`). Keep test-only helpers in `src/test/java/.../util/` if they are not needed by main code.

**What to put in util classes:**

| Util (example) | Responsibility | Why not elsewhere |
|----------------|----------------|--------------------|
| **Timestamp / date** | Current timestamp, expiry for orders, nonce | Reused across auth and order; single place for format/clock |
| **JSON** | Serialize request POJO to string, deserialize response body to response POJO | Can wrap Jackson ObjectMapper; used by BaseService or tests |
| **Retry / poll** | Poll until condition (e.g. market resolved, position appears) with timeout and interval | Reused in multiple tests; keeps test method focused on “what” not “how long to wait” |
| **Env / config helper** | Get env var with fallback, or load a property (if not already in ApiConfig) | Avoid duplicating “get from env then sysprop then default” |
| **Signature / request building** | If we ever build EIP-712 payload or call sig-server from Java, a small helper to build the payload or HTTP call | Keeps services thin |

**What not to put in utils:** Anything that is clearly “one API area” (e.g. “build login body”) belongs in the service or a dedicated builder; HTTP calls stay in BaseService/services. Business rules (e.g. “expected balance = initial + pnl”) can live in the test or a small “balance expectation” helper in test scope if you want to reuse.

**Summary:** Utils = cross-cutting, reusable, non-API-specific helpers (time, JSON, retry, env). API-shaped logic stays in services and POJOs.

---

## 4. Where Do We Write Assertion Test Cases?

**Where:** In **test classes** only. Convention: `src/test/java/com/pred/apitests/` (or under `test/` subpackage) in classes named `*Test.java`, e.g. `LoginTest`, `DepositTest`, `OrderValidationTest`, `MatchingEngineTest`, `BalanceEngineTest`, `SettlementTest`. These are the “assertion test cases.”

**Who does what:**

- **Service layer:** Calls API, returns `Response` (or parsed response POJO). Services do **not** assert; they just perform the action and return.
- **Test class:** Holds the test methods. Each test method: (1) calls service(s), (2) gets response (or POJO), (3) **asserts** status code, body fields, and state (balance, position, etc.). So all “assertion test cases” live in these test methods.

**What we use for assertions:**

- **RestAssured:** `.then().statusCode(200)`, `.body("path", equalTo(value))` for status and inline JSON-path checks. Good for “response is 202” and “field X equals Y.”
- **AssertJ:** `assertThat(parsedValue).isNotBlank()`, `.isEqualTo()`, `.isGreaterThanOrEqualTo(0)` for logic on extracted values (e.g. token present, balance >= 0, available = total - reserved). Use when you have a POJO or extracted value and want readable, chainable assertions.
- **Both:** RestAssured for status and simple body checks; AssertJ for computed or multi-field logic (e.g. balance deltas, position state). Per PHASE1_DESIGN and OBJECTIVES_AND_ALIGNMENT we do **not** hardcode expected balances; we derive expected from prior state and rules, then assert.

**Suite structure:** Test classes are grouped into TestNG suites (e.g. Order Validation, Matching Engine, Balance Engine, Settlement). The suite XML decides which classes run together; the assertion logic stays inside the test methods of those classes.

**Summary:** Assertion test cases = test methods in `*Test.java` classes. Services return data; tests assert on it using RestAssured + AssertJ. No assertions in services or in POJOs.

---

## 5. What About Database Connection?

**Current stance:** Database is **not** in scope for the first phase of automation. FRAMEWORK.md and PHASE1_DESIGN.md treat it as **future** (e.g. “Database Integration (Future)”, “Pending inputs for JDBC”).

**Why DB might be used later:**

- **Source of truth check:** After an API call (e.g. place order, settlement), compare API response with DB state (e.g. orders table, positions table, balance table) to validate consistency.
- **Data-driven tests:** Fetch test data (e.g. market id, user id) from DB instead of only env/properties.
- **E2E integrity:** Ensure API and persistence stay in sync (e.g. balance in API matches balance in DB after settlement).

**What we need before adding DB (from PHASE1_DESIGN section 10):**

- DB engine/version, connection details per env (host, port, db, user, password/secret source).
- SSL/TLS and read-only access for the test runner.
- Tables/columns to validate (orders, positions, balances) and mapping to API concepts (order id, user id, market id).
- Assertion rules: exact match vs tolerance, immediate vs eventual consistency.

**Where DB code would live (when we add it):**

- A dedicated package, e.g. `com.pred.apitests.db` or `integration/`, with:
  - Connection/config (from env, no credentials in code).
  - Small “repository” or “query” helpers that return data (or POJOs) for tests to use.
- Tests that need DB would call these helpers and then assert API response vs DB state. Still no assertions inside the DB layer; only data fetch.

**Summary:** No database connection or DB code today. When we add it: config and queries in a dedicated package; tests use that data for assertions. All prerequisites (connection details, tables, mapping, assertion rules) are listed in PHASE1_DESIGN section 10 and should be agreed before implementation.

---

## Quick Reference

| Topic | Where | What |
|-------|--------|------|
| Enums | model (e.g. model/enums) | OrderSide, OrderType, position/market state; fixed literals for requests and assertions |
| Request POJOs | model/request/ | Body shape we send; optional Builder |
| Response POJOs | model/response/ | Body shape we parse; for type-safe assertions and passing data to next step |
| Utils | util/ | Time, JSON, retry/poll, env helper; cross-cutting, non-API-specific |
| Assertion test cases | *Test.java test methods | RestAssured + AssertJ; services return, tests assert |
| Database | Future; package e.g. db/ | Config and queries only; tests assert API vs DB when we add it |
