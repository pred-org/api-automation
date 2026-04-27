# API Automation (RestAssured)
<!-- Maintained by Abhishek Mishra -->

RestAssured-based API test project. **TestNG** for tests; **GitHub Actions** for CI/CD (when enabled).

The suite covers auth, enable trading, deposits, portfolio, orders, orderbook, market discovery, cancel flows, multi-user matching, balance checks, and integrity scenarios. See [docs/OBJECTIVES_AND_ALIGNMENT.md](docs/OBJECTIVES_AND_ALIGNMENT.md) for goals and success criteria.

## Requirements

- **Java 17+**
- **Maven 3.6+**

## Repository structure

- `docs/` — Technical documentation ([docs/DOCS-INDEX.md](docs/DOCS-INDEX.md))
- `src/` — API test suite (RestAssured + TestNG)
- `sig-server/` — Signing server (starts automatically during `mvn test` or via `start-sig-server.sh`)
- `tools/k6/` — k6 load and stress test scripts
- `tools/scripts/` — Utility scripts (for example order ID capture for k6)
- `tools/ops/` — Ops tools (market verification server and verify-market shell helpers)

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

Default suite: `src/test/resources/suite.xml`. **Full command reference** (alternate suites, auth, two-user flow, Allure, `tools/`): [docs/COMMANDS.md](docs/COMMANDS.md).

## CI/CD (GitHub Actions)

Workflow [.github/workflows/api-tests.yml](.github/workflows/api-tests.yml) runs on push/PR to `main` or `master`. When the repo is on GitHub, it will run `mvn clean test`. Add repository secrets (e.g. `API_BASE_URI_PUBLIC`, `ACCESS_TOKEN`, `MARKET_ID`) and uncomment the `env` block in the workflow when you need them.

## What you need to start

- **Prerequisites checklist:** [docs/WHAT_YOU_NEED_TO_START.md](docs/WHAT_YOU_NEED_TO_START.md).

## Framework and design

- **Documentation index:** [docs/DOCS-INDEX.md](docs/DOCS-INDEX.md)
- **Objectives and alignment:** [docs/OBJECTIVES_AND_ALIGNMENT.md](docs/OBJECTIVES_AND_ALIGNMENT.md)
- **Framework (stack, structure, filters, CI):** [docs/FRAMEWORK.md](docs/FRAMEWORK.md)
- **HTTP API reference (canonical):** [docs/API_DOCUMENTATION.md](docs/API_DOCUMENTATION.md)
- **Test cases and coverage:** [docs/TESTCASES-COVERED.md](docs/TESTCASES-COVERED.md)
- **Commands (Maven, TestNG, tooling):** [docs/COMMANDS.md](docs/COMMANDS.md)
- **Per-repo file map:** [docs/FILE-BY-FILE-INVENTORY.md](docs/FILE-BY-FILE-INVENTORY.md)

## Next steps

1. Configure env from `.env.template` and run auth tests to produce session files.
2. Run the full suite against your target environment.
3. Extend assertions per OBJECTIVES_AND_ALIGNMENT (position correctness, balance accuracy, no duplicate settlement).
