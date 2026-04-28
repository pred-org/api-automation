# api-automation repository layout

High-level map of directories, env files, main code, tests, and tooling. Edit `.env` for keys and URLs; session files are generated and can be removed automatically when `PRIVATE_KEY` / `PRIVATE_KEY_2` changes (see `Config` and `SessionFileWriter`).

```
api-automation/
│
├── .env                    ← Your keys, URLs, market config (only file you edit for new users)
├── .env.session            ← Auto-generated after login (user 1 tokens, auto-deleted on key change)
├── .env.session2           ← Auto-generated after login (user 2 tokens, auto-deleted on key change)
├── .env.template           ← Setup reference for new devs
├── .env.tracker            ← Excel/Google Sheets webhook URL
├── pom.xml                 ← Maven build config, dependencies, surefire settings
├── start-sig-server.sh     ← Helper script to launch sig-server
│
├── src/main/java/com/pred/apitests/
│   ├── base/
│   │   └── BaseService              ← RestAssured HTTP client wrapper (all services extend this)
│   │
│   ├── config/
│   │   ├── Config                   ← Loads .env + testdata.properties into system properties
│   │   └── KafkaConfig              ← Kafka broker/SSL connection settings
│   │
│   ├── filters/
│   │   └── LoggingFilter            ← Logs HTTP request/response for debugging
│   │
│   ├── listeners/
│   │   ├── TestListener             ← Logs test start/pass/fail/skip events
│   │   ├── ExcelTrackerListener     ← Writes run results to test_run_tracker.xlsx
│   │   └── SlackNotificationListener ← Posts suite summary to Slack channel
│   │
│   ├── model/
│   │   ├── request/                 ← Request DTOs (Login, Deposit, PlaceOrder, SignOrder, Cashflow)
│   │   └── response/                ← Response DTOs (Login, Balance, Sign, Prepare, etc.)
│   │
│   ├── service/                     ← API service layer (one class per API domain)
│   │   ├── AuthService              ← Login, API key creation, token refresh
│   │   ├── SignatureService         ← Signs orders/transactions via sig-server
│   │   ├── OrderService             ← Place, cancel, get open orders
│   │   ├── PortfolioService         ← Balance, positions, trade history
│   │   ├── DepositService           ← Internal deposit + cashflow
│   │   ├── EnableTradingService     ← Prepare + execute enable-trading
│   │   ├── MarketDiscoveryService   ← Discover markets by canonical name
│   │   ├── SettlementService        ← Settlement / resolve helpers
│   │   └── UserService              ← User profile/info
│   │
│   └── util/
│       ├── TokenManager             ← Singleton — holds access token, refresh, EOA, proxy, private key
│       ├── SecondUserContext        ← Loads user 2 session from .env.session2 or env vars
│       ├── UserSession              ← Per-user session object (token, proxy, EOA, key)
│       ├── SessionFileWriter        ← Writes tokens to .env.session / .env.session2 after login
│       ├── MarketContext            ← Caches market/sub-market IDs from discovery
│       ├── AutomationFixtureBootstrap ← Reuses only-for-automation-N canonical names
│       └── KafkaEventWaiter         ← Kafka consumer for event-driven assertions (optional / advanced)
│
├── src/test/java/com/pred/apitests/
│   ├── FrameworkSmokeTest           ← Verifies Config loads correctly
│   ├── LoginTest                    ← Basic login smoke test
│   ├── HealthCheckTest              ← API health + is_enabled_trading check
│   │
│   ├── test/                        ← Active suite tests (see suite.xml)
│   │   ├── AuthFlowTest             ← User 1: create API key → sign → login → store tokens
│   │   ├── AuthFlowTestUser2        ← User 2: same flow with PRIVATE_KEY_2
│   │   ├── EnableTradingTest        ← User 1: prepare → sign → execute enable-trading
│   │   ├── EnableTradingTestUser2   ← User 2: same
│   │   ├── DepositTest              ← User 1: internal deposit (skips if balance sufficient)
│   │   ├── DepositTestUser2         ← User 2: same
│   │   ├── OrderTest                ← Place order, verify in open-orders
│   │   ├── OrderTestUser2           ← User 2: same
│   │   ├── CancelOrderTest          ← Cancel single order, verify removed
│   │   ├── CancelOrderTestUser2     ← User 2: same
│   │   ├── CancelAllOrdersTest      ← Place 3 orders, cancel-all, verify cleared
│   │   ├── OrderbookTest            ← Verify bid quantity changes after SHORT
│   │   ├── OrderFlowTest            ← Two-user match, positions, PnL, trade history
│   │   ├── PortfolioTest            ← Balance, positions, trade history structure
│   │   ├── BalanceReservationTest   ← Scoped balance drops on order, restores on cancel
│   │   ├── BalanceServiceTest       ← Balance API response validation
│   │   ├── BalanceIntegrityTest     ← Cross-check balance consistency
│   │   ├── ClosePositionTest        ← Open position → close via reduceOnly → verify
│   │   └── MarketDiscoveryTest      ← Leagues, fixtures, discover endpoints
│   │
│   ├── experimental/                ← Not in suite.xml — reference / on-demand use
│   │   ├── KafkaConnectionTest      ← Kafka connectivity diagnostic
│   │   ├── MarketFamilyOrderTest    ← Multi-market order experiment
│   │   ├── MarketLifecycleTest      ← Market state transition test
│   │   ├── NewUserLoginDepositTest  ← Full new-user bootstrap test
│   │   ├── OneTimeEnableTradingTest ← Standalone enable-trading runner
│   │   ├── ResolveFixtureTest       ← Sports-info / fixture resolution helper
│   │   └── EnableUserScript         ← CLI tool to login+enable a user by private key
│   │
│   ├── base/
│   │   └── BaseApiTest              ← Test base class — token setup, market resolution, refresh
│   │
│   └── util/
│       ├── PollingUtil              ← Poll with timeout (used for order visibility checks)
│       ├── RetryAnalyzer            ← TestNG retry failed tests (MAX_RETRY configurable)
│       ├── RetryListener            ← Attaches RetryAnalyzer to all test methods
│       ├── SchemaValidator          ← Validates API responses against JSON schemas
│       └── TestPreConditions        ← Common precondition checks
│
├── src/test/resources/              ← TestNG suite, properties, JSON schemas (not under java/)
│   ├── suite.xml                    ← Active TestNG suite (defines test order + listeners)
│   ├── testdata.properties          ← Market IDs, order params, deposit amount
│   ├── schemas/                     ← JSON schemas for response validation
│   └── logback-test.xml             ← Test logging config
│
├── sig-server/                      ← Node.js signature server (port 5050)
│   ├── signatures/server.js         ← Main server — signs login, orders, safe-approval
│   ├── api/                         ← Standalone API scripts (create key, place order, etc.)
│   ├── execution/                 ← Enable-trading execution helper
│   └── scripts/                   ← One-off utility scripts
│
├── tools/
│   ├── k6/                          ← Load test scripts (k6)
│   ├── ops/                         ← Market smoke-test helpers (shell)
│   └── scripts/                   ← Shell helpers (e.g. set_env), when present
│
├── docs/                            ← Markdown documentation (framework, API, setup, walkthrough)
└── documents/                       ← Private notes, exports, xlsx (gitignored)
```

## See also

- [flow.md](flow.md) — example ordered flow of test blocks (illustrative run layout)
- [DOCS-INDEX.md](DOCS-INDEX.md) — full doc index
- [FILE-BY-FILE-INVENTORY.md](FILE-BY-FILE-INVENTORY.md) — detailed file map
