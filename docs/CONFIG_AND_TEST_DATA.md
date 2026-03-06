# Config and Test Data Reference

Where all configuration and test data live, and what you can change.

---

## 1. Java tests (Maven / TestNG)

Config is read by `Config.java` with this **priority** (first non-blank wins):

1. **System property** (e.g. `-Dapi.base.uri.public=...`)
2. **Environment variable** (e.g. `API_BASE_URI_PUBLIC`)
3. **Classpath properties**: `application.properties` then `testdata.properties`

### 1.1 Properties files (what you can edit)

| File | Purpose |
|------|--------|
| `src/test/resources/application.properties` | Optional overrides; mostly comments. Prefer testdata for URLs. |
| `src/test/resources/testdata.properties` | Main test data: base URLs, market, token, wallet, deposit, optional API key. |

### 1.2 Config keys and where to change them

| Config key (property or env) | Used for | Change in |
|------------------------------|----------|-----------|
| `api.base.uri.public` / `API_BASE_URI_PUBLIC` | Public API base (login, place-order, etc.) | testdata.properties or env |
| `api.base.uri` / `API_BASE_URI` | Fallback if public not set | testdata.properties or env |
| `api.base.uri.internal` / `API_BASE_URI_INTERNAL` | Internal API (create-api-key, deposit) | testdata.properties or env |
| `api.key` / `API_KEY` | API key for login | testdata.properties (optional) or env; leave unset to create key per run |
| `sig.server.url` / `SIG_SERVER_URL` | Sig-server URL (default http://localhost:5050) | testdata.properties or env |
| `eoa.address` / `EOA_ADDRESS` | Wallet (EOA) address | testdata.properties or env |
| `private.key` / `PRIVATE_KEY` | Wallet private key for signing | testdata.properties or env (sensitive; prefer env) |
| `market.id` / `MARKET_ID` | Market ID for order/portfolio | testdata.properties or env |
| `token.id` / `TOKEN_ID` | Outcome token for place-order | testdata.properties or env |
| `deposit.amount` / `DEPOSIT_AMOUNT` | Deposit amount (long) | testdata.properties or env |
| `slack.webhook.url` / `SLACK_WEBHOOK_URL` | Slack Incoming Webhook URL; post suite summary. Blank = no notification (unless Bot Token used) | env or testdata.properties |
| `slack.bot.token` / `SLACK_BOT_TOKEN` | Slack Bot Token (xoxb-...) for Web API; enables thread reply with per-test list. Use with SLACK_CHANNEL | env or testdata.properties |
| `slack.channel` / `SLACK_CHANNEL` | Slack channel ID (e.g. C01234...) for chat.postMessage. Required when using SLACK_BOT_TOKEN | env or testdata.properties |

**Order test data in testdata.properties** (`order.side`, `order.price`, `order.quantity`, `order.type`): currently **not** read by `Config.java`; `OrderTest` uses hardcoded values (e.g. price "30", quantity "100"). To make them configurable, add `Config.getOrderPrice()`, etc., and wire from testdata.

**Deposit internal token:** `DepositService` uses `INTERNAL_DEPOSIT_TOKEN` from **env only** (not in Config). Set in env when the backend requires it.

### 1.3 Allure

| File | Purpose |
|------|--------|
| `src/test/resources/allure.properties` | Allure results directory (e.g. `target/allure-results`). |

### 1.4 Test selection (what runs)

| File | Purpose |
|------|--------|
| `src/test/resources/suite.xml` | TestNG suite: which test classes run and in what order. Edit to include/exclude tests. |

### 1.5 Slack notification

After the suite finishes, a summary is sent to Slack: **total | passed | failed | skipped**. You can use either the **Web API (Bot Token)** or an **Incoming Webhook**.

**Option A – Web API (recommended for thread reply):**

1. In Slack: create a **Bot** (Settings / Integrations / Apps / Build / Create New App; add Bot User, install to workspace). Under **OAuth & Permissions**, add the Bot Token Scope **`chat:write`** (required for `chat.postMessage`). Reinstall the app to the workspace if you add the scope after creation. Copy the **Bot User OAuth Token** (starts with `xoxb-`).
2. Invite the bot to the channel (e.g. `/invite @YourBot`) and get the **channel ID** (right-click channel / View channel details / copy ID).
3. Set `SLACK_BOT_TOKEN=xoxb-...` and `SLACK_CHANNEL=C01234...` (env or testdata.properties). When both are set, the listener uses `chat.postMessage`: it posts the summary, then a **thread reply** listing each test with status: `[OK] testName - passed`, `[FAIL] testName - failed`, `[SKIP] testName - skipped`. If the Web API fails (e.g. missing `chat:write` scope), and `SLACK_WEBHOOK_URL` is set, the summary is sent via the webhook instead.

**Option B – Incoming Webhook (single message only):**

1. In Slack: create an **Incoming Webhook** (Settings / Integrations / Incoming Webhooks / Add to Slack; choose the channel).
2. Set `SLACK_WEBHOOK_URL=https://hooks.slack.com/services/...` (env or testdata.properties). No thread reply; only the one-line summary is sent.
3. If only the webhook is set (no Bot Token + channel), the webhook is used. If Bot Token and channel are set, the Web API is tried first; on failure, the webhook is used when set.

If neither Bot Token+channel nor webhook is set, no request is sent.

Listener: `SlackNotificationListener` (registered in `suite.xml`).

---

## 2. Sig-server (Node)

### 2.1 Main config file

| File | Purpose |
|------|--------|
| `sig-server/config.js` | Single source for wallet, API key, market/token, EIP-712 domain. Env vars override. |

### 2.2 What you can change in `sig-server/config.js`

| Key | Used for | Override with env |
|-----|----------|--------------------|
| `PRIVATE_KEY` | Signing (login, sign-order, sign-create-proxy, sign-safe-approval) | `PRIVATE_KEY` |
| `EOA_ADDRESS` | Wallet address (optional if PRIVATE_KEY set; derived otherwise) | `EOA_ADDRESS` |
| `API_KEY` | Login; leave null to create via create-api-key flow | `API_KEY` |
| `USER_ID` | Filled by login; do not set manually | `USER_ID` |
| `PROXY` | Filled by login; do not set manually | `PROXY` |
| `MARKET_ID` | Place-order / sign-order market | `MARKET_ID` |
| `TOKEN_ID` | Place-order token | `TOKEN_ID` |
| `CHAIN_ID`, `VERIFYING_CONTRACT`, `DOMAIN_NAME`, `DOMAIN_VERSION` | EIP-712 domain (UAT fixed; change for prod) | Not overridden by env in config |

### 2.3 Sig-server scripts (env overrides)

These use `config.js` and can override via env:

- **place-order.js**: `MARKET_ID`, `TOKEN_ID`, `PLACE_ORDER_PRICE`, `PLACE_ORDER_QTY` (defaults: 30, 100).
- **create-api-key-client.js**: `API_BASE_URI_INTERNAL_HOST` (default: api-internal.uat-frankfurt.pred.app).
- **enable-trading.js**: `API_KEY`, `USE_PERSONAL_SIGN`.
- **get-new-access-tokens.js**, **multi-user-config.js**: `MARKET_ID`, `TOKEN_ID`, `USER1_PRIVATE_KEY`, `USER1_EOA`, etc.

### 2.4 Multi-user and cached tokens

| File | Purpose |
|------|--------|
| `sig-server/multi-user-config.js` | Multi-user load tests: per-user keys, API keys, MARKET_ID, TOKEN_ID. Optional. |
| `sig-server/users-tokens.json` | Cached tokens/proxy/userId and order params; can be generated by scripts. Do not commit secrets. |

---

## 3. Environment and secrets

| File | Purpose |
|------|--------|
| `.env.template` | Template for env vars used by Maven/TestNG and sig-server. Copy to `.env` and set values; do not commit `.env`. |
| `.env` | Local overrides (if your setup loads it; Java Config does **not** read .env; use `export` or -D for Maven). |

**Recommendation:** Keep real secrets in **env vars** or a local script (e.g. `source scripts/set_env.sh`). Avoid committing `PRIVATE_KEY`, `API_KEY`, or tokens in `testdata.properties` or `config.js`.

---

## 4. Quick reference: “I want to change…”

| I want to… | Change here |
|------------|-------------|
| …run tests against another environment | `api.base.uri.public` / `api.base.uri.internal` in testdata.properties or env |
| …use a different wallet for login/signing | `PRIVATE_KEY` and `EOA_ADDRESS` in testdata.properties or env (Java); same in sig-server/config.js or env for Node |
| …point Java to another sig-server | `SIG_SERVER_URL` in testdata.properties or env |
| …use a different market/token for orders | `MARKET_ID` and `TOKEN_ID` in testdata.properties or env (Java); same in sig-server/config.js or env (Node) |
| …change deposit amount | `deposit.amount` / `DEPOSIT_AMOUNT` in testdata.properties or env |
| …change order price/quantity in Node place-order script | Env `PLACE_ORDER_PRICE`, `PLACE_ORDER_QTY` or edit defaults in sig-server/api/place-order.js |
| …change order price/quantity in Java OrderTest | Edit hardcoded values in OrderTest.java (or add Config getters and testdata keys) |
| …run a subset of tests | Edit `src/test/resources/suite.xml` |
| …switch EIP-712 domain (e.g. prod) | Edit `sig-server/config.js`: `CHAIN_ID`, `VERIFYING_CONTRACT`, `DOMAIN_NAME`, `DOMAIN_VERSION` |
| …post test summary to Slack | Set `SLACK_WEBHOOK_URL` (webhook only) or `SLACK_BOT_TOKEN` + `SLACK_CHANNEL` (Web API + thread with per-test list). |
