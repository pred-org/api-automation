# API Automation (RestAssured)

RestAssured-based API test project. **TestNG** for tests; **GitHub Actions** for CI/CD (when enabled).

**Phase 1** automates a single user trading flow: auth → market selection → place order → position check → resolution → redemption → balance verification. See [docs/PHASE1_DESIGN.md](docs/PHASE1_DESIGN.md) for scope, services, flow, and validation criteria.

## Requirements

- **Java 17+**
- **Maven 3.6+**

## Project layout

```
api-automation/
├── .github/workflows/
│   └── api-tests.yml               # GitHub Actions (run tests on push/PR)
├── pom.xml
├── README.md
├── src/test/java/com/pred/apitests/
│   ├── config/
│   │   └── ApiConfig.java          # Base URI, no auth
│   ├── base/
│   │   └── BaseApiTest.java        # RestAssured base spec (TestNG)
│   ├── FrameworkSmokeTest.java     # Framework smoke test
│   └── HealthCheckTest.java        # Placeholder (disabled)
└── src/test/resources/
    ├── suite.xml                   # TestNG suite (used by mvn test)
    ├── application.properties      # Optional overrides
    └── testdata.properties         # Base URLs, market/order data
```

## Run tests

```bash
cd api-automation
mvn test
```

Tests are driven by the TestNG suite `src/test/resources/suite.xml`. Use a different suite:

```bash
mvn test -Dsuite=src/test/resources/other-suite.xml
```

**Allure report:** After `mvn test`, generate and open the report:

```bash
mvn allure:serve
```

Override base URI:

```bash
mvn test -Dapi.base.uri=https://your-api-host.com
# or
export API_BASE_URI=https://your-api-host.com
mvn test
```

## CI/CD (GitHub Actions)

Workflow [.github/workflows/api-tests.yml](.github/workflows/api-tests.yml) runs on push/PR to `main` or `master`. When the repo is on GitHub, it will run `mvn clean test`. Add repository secrets (e.g. `API_BASE_URI_PUBLIC`, `ACCESS_TOKEN`, `MARKET_ID`) and uncomment the `env` block in the workflow when you need them for Phase 1.

## What you need to start

- **Prerequisites checklist:** [docs/WHAT_YOU_NEED_TO_START.md](docs/WHAT_YOU_NEED_TO_START.md).
- **Design discussion (enums, POJOs, utils, assertions, DB):** [docs/DESIGN_DISCUSSION_ENUMS_POJOS_UTILS_ASSERTIONS_DB.md](docs/DESIGN_DISCUSSION_ENUMS_POJOS_UTILS_ASSERTIONS_DB.md). Environment URLs, API key, test users (EOA + signing), market/token/deposit config, API contracts (what is documented vs TBD), and decisions (auth strategy, two users, resolution). Use this before implementing flows.

## Framework and design

- **Objectives and alignment:** [docs/OBJECTIVES_AND_ALIGNMENT.md](docs/OBJECTIVES_AND_ALIGNMENT.md). What we validate: state-transition (auth, order, matching, position, balance, settlement); two users (buyer/seller); balance and settlement rules; test suites and success criteria. Use this to stay aligned.
- **Framework (stack, structure, flow):** [docs/FRAMEWORK.md](docs/FRAMEWORK.md). Java + Maven + Rest Assured + **TestNG**, Service Object pattern, POJO + Builder, Log4j2, Filters, TestNG Listeners, Surefire, **GitHub Actions**. Full execution flow and package mapping.
- **Phase 1 scope and flow:** [docs/PHASE1_DESIGN.md](docs/PHASE1_DESIGN.md).
- **API reference (from pred-load-tests):** [docs/API_REFERENCE_FROM_LOAD_TESTS.md](docs/API_REFERENCE_FROM_LOAD_TESTS.md).

## Next steps

1. Provide API contracts (paths, request/response) for each service used in the flow.
2. Implement flow with configurable inputs (env/properties + data from previous steps).
3. Add assertions per key validation criteria (position correctness, balance accuracy, no duplicate settlement, no negative balance).
