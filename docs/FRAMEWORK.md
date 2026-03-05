# End-to-End API Automation Framework

A scalable API automation framework for this project. 
Built with: **Java + Maven**, **Rest Assured**, **TestNG** (not JUnit), **Service Object Pattern**, **POJO models**, **Log4j2**, **Filters (interception)**, **TestNG Listeners**, **Maven Surefire**, **GitHub Actions** for CI/CD, artifact archiving, and scheduled runs. Covers auth (access token, proxy wallet), market/order flow, deposit, positions, and balance verification. Signatures used: **EIP-712** (login CreateProxy, place order Order) and **EIP-1193** (transaction hash for safe-approval execute).

---



## 1. Technology Stack

| Component | Choice |
|-----------|--------|
| Language / Build | Java, Maven |
| API automation | Rest Assured |
| Test execution | TestNG |
| Design pattern | Service Object Model |
| Request/Response | POJO models (request + response) |
| Logging | Log4j2 |
| Interception | Filters (request/response) |
| Test lifecycle | TestNG Listeners |
| Execution | Maven Surefire Plugin |
| CI | GitHub Actions |
| Extras | Artifact archiving, scheduled runs |

**Signatures (do not mix):** Login and place order use **EIP-712** (typed data). Enable-trading execute uses **EIP-1193** (sign the `transactionHash` from prepare, e.g. personal_sign). Each step uses the correct signature type; see API reference for details.

---

## 2. Project Structure

Package base: `com.pred.apitests`.

```
src/main/java/com/pred/apitests/
├── base/
│   └── BaseService.java           # Wrapper over Rest Assured; filters, base URI, headers
├── service/
│   ├── AuthService.java
│   ├── AccountService.java
│   └── ...                        # One service per API area
├── model/
│   ├── request/                   # Request POJOs (e.g. SignUpRequest, LoginRequest)
│   └── response/                  # Response POJOs (e.g. LoginResponse, BalanceResponse)
├── listeners/
│   └── TestListener.java         # TestNG ITestListener
└── filters/
    └── LoggingFilter.java         # Intercepts request + response; logs details

src/test/java/com/pred/apitests/
└── test/
    └── *Test.java                 # API test classes (call services, assert)

src/test/resources/
├── log4j2.xml
├── testdata.properties (or testdata/)
├── suite.xml                      # TestNG suite for CI
└── ...
```

---

## 3. Service Object Model

**Goal:** No raw `given().when().then()` in test classes. All HTTP is behind services.

- **BaseService:** Single wrapper over Rest Assured. Holds base URI, default headers, and **registers filters** (e.g. LoggingFilter) in a **static block** so they run once for all requests.
- **Service classes (AuthService, AccountService, etc.):** One class per API area. Each method:
  - Calls a base method (e.g. `post()`, `get()`, `put()`, `delete()`)
  - Passes path, body, optional headers
  - Returns `Response`
- **Benefits:** No duplication, easier maintenance, one place for base URI and headers.

---

## 4. Request & Response (POJO + Builder)

- **Request models:** POJOs for request bodies. Use **Builder pattern** so tests stay clean and order-independent. Constructor kept private; build via `new XRequest.Builder().field(value).build()`.
- **Response models:** POJOs that map JSON response (e.g. Gson/Jackson) for type-safe assertions.
- **Benefits:** Order-independent parameters, clean test code, single place for payload shape.

---

## 5. Logging (Log4j2)

- **Dependencies:** log4j-api, log4j-core.
- **Config:** `log4j2.xml` with:
  - **Appenders:** Console, File (e.g. `logs/`).
  - **Loggers:** Root and package-specific.
- **Pattern:** Timestamp, thread name, log level, class name, message, newline.

---

## 6. TestNG Listeners (ITestListener)

- **Role:** Capture test lifecycle (onTestStart, onTestSuccess, onTestFailure, onTestSkipped, onStart/onFinish for suite).
- **Use:** Log test name, status, description (e.g. `logger.info("Test Started: " + result.getMethod().getMethodName())`).
- **Limitation:** Listeners do **not** have access to request/response (payload, headers, base URI, status code, body). For that we use **Filters**.

---

## 7. Filters (Interception)

- **Role:** Sit between test and server. Intercept **before** request hits server and **after** response returns.
- **Before request:** Capture and log base URI, headers, body. Then call `ctx.next()` to continue.
- **After response:** Capture and log status code, response body, response headers; then return response to test.
- **LoggingFilter:** Implements `Filter`; inside it: `logRequest()`, then `ctx.next()`, then `logResponse()` on the response.
- **Sensitive data:** In the filter, mask auth headers (e.g. replace value with `****`) before logging.

---

## 8. Attach Filter Globally (BaseService)

- In **BaseService**, use a **static block** to register the filter once:
  - `RestAssured.filters(new LoggingFilter());`
- **Why static:** Runs once per JVM; no duplicate registration; safe with parallel execution.

---

## 9. Running from Terminal (CI-Ready)

- **Surefire:** Configure TestNG suite via parameter, e.g. `<suiteXmlFile>${suite}</suiteXmlFile>` (or equivalent).
- **Command:** `mvn clean test -Dsuite=suite.xml`
- Framework runs without IDE; same command used in GitHub Actions.

---

## 10. GitHub Actions

- **Steps:** Push to GitHub; add workflow YAML.
- **Job:** Set up Java, run `mvn clean test -Dsuite=suite.xml`.
- **Scheduled runs:** Use cron (e.g. convert IST to UTC; example `00 18 * * *` for 11:30 PM IST).
- **Artifacts:** Upload `logs/`, test reports (e.g. `target/surefire-reports`), or Allure results via `actions/upload-artifact`.
- **Test results in UI:** Use something like `dorny/test-reporter` to read `target/surefire-reports/test-*.xml` and show results in the GitHub Actions summary.

---

## 11. Masking Sensitive Data

- In **LoggingFilter** (or similar), when logging request headers, replace authorization/token header values with `****` so secrets never appear in logs or artifacts.

---

## 12. Database Integration (Future)

- Use JDBC to fetch data, map to POJO, pass into API request (e.g. for data-driven or DB-backed assertions). Details in PHASE1_DESIGN.md section 10 (JDBC/Kafka/E2E inputs).

---

## 13. Full Execution Flow

```
Test class
    |
    v
Service layer (e.g. AuthService.getToken())
    |
    v
BaseService (post/get/put/delete)
    |
    v
LoggingFilter – intercept request, log (masked), then ctx.next()
    |
    v
Server
    |
    v
LoggingFilter – intercept response, log status + body
    |
    v
Return response to test
    |
    v
Test asserts on response
    |
    v
TestNG Listener logs test status (pass/fail/skip)
    |
    v
Surefire generates XML reports
    |
    v
GitHub Actions runs (or scheduled)
    |
    v
Artifacts uploaded (logs, reports)
    |
    v
Test results published to dashboard (e.g. test-reporter)
```

---

## 14. Mapping to This Repo

| Concept | In this repo |
|---------|--------------|
| BaseService | com.pred.apitests.base.BaseService (to add) |
| Services | com.pred.apitests.service.* (to add) |
| Request/Response POJOs | com.pred.apitests.model.request / .response (to add) |
| TestListener | com.pred.apitests.listeners.TestListener (to add) |
| LoggingFilter | com.pred.apitests.filters.LoggingFilter (to add) |
| Test classes | com.pred.apitests.test (to add) |
| Base test / config | Existing: BaseApiTest, ApiConfig; keep and use from BaseService |

Implement in order: BaseService + filters, then services, then POJOs, then listeners, then CI.
