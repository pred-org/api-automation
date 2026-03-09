# k6 load test: place order + cancel order (rate limit)

Runs only **place order** and **cancel order** in a loop to find API rate limits. No PnL or balance checks.

## Prerequisites

- [k6](https://grafana.com/docs/k6/latest/set-up/install-k6/) installed.
- Sig-server running (for `/sign-order`), e.g. `node sig-server/signatures/server.js` or your usual start.
- Valid session: you must set `ACCESS_TOKEN`, `REFRESH_COOKIE`, `EOA`, `PROXY`, `USER_ID` (see below).

## Getting session env vars

**Option A (recommended):** Run the Java auth flow once; the suite writes the session to `.env.session` in the project root. Then source it and run k6:

```bash
mvn test -Dtest=AuthFlowTest
source .env.session
K6_QUICK=1 k6 run k6/place-cancel-rate-limit.js
```

**Option B:** Copy from test output and export manually: run `mvn test -Dtest=AuthFlowTest`, then from logs get access token (ACCESS_TOKEN_FOR_POSTMAN), refresh cookie (Cookie: refresh_token=...), EOA, proxy, user_id, and export them before running k6.

Optional env (defaults are UAT): `BASE_URL`, `SIG_SERVER_URL`, `MARKET_ID`, `TOKEN_ID`.

## Run

Default run (5 min ramp: 2 to 30 VUs for rate-limit probing):

```bash
k6 run k6/place-cancel-rate-limit.js
```

1-minute smoke test (20 VUs by default, to confirm orders on the frontend):

```bash
source .env.session
K6_QUICK=1 k6 run k6/place-cancel-rate-limit.js
```

Higher load (target >20 RPS for place/cancel): use more VUs and/or less sleep between cycles:

```bash
source .env.session
K6_QUICK=1 K6_VUS=50 k6 run k6/place-cancel-rate-limit.js
# or with shorter pause:
K6_QUICK=1 K6_VUS=50 K6_ITER_SLEEP=0.1 k6 run k6/place-cancel-rate-limit.js
```

- `K6_VUS`: VUs for the quick run (default 20).
- `K6_ITER_SLEEP`: seconds to sleep after each place+cancel (default 0.2). Lower = higher RPS per VU.
- `K6_CONSUMER_LAG`: optional; added to the failure report for the service owner (e.g. Kafka consumer lag, kadek details). Example: `K6_CONSUMER_LAG="partition 0 lag: 120; consumer group X behind"`
- `K6_REPORT_EXTRA`: optional; any extra line(s) added to the report (e.g. other backend metrics).

Use **real** credentials (or run AuthFlowTest once and use `source .env.session`). Placeholders cause 401 and no orders on the frontend.

## Connecting Kadek UAT for consumer lag

To **fetch consumer lag from Kadek UAT** and include it in the report, the script calls an HTTP endpoint at the end of the run. You need the **lag API URL** for your UAT environment (and auth if required).

1. **Set the lag URL** (one of):
   - `KADEK_LAG_URL` – full URL that returns consumer lag (e.g. Confluent-style or your internal API).
   - `KADEK_UAT_LAG_URL` – same, alternative name.

2. **Optional auth**: if the endpoint requires a bearer token or API key, set:
   - `KADEK_LAG_AUTH_HEADER="Bearer <token>"` (or `"ApiKey <key>"`).

**Example (Confluent-style API):**

```bash
export KADEK_LAG_URL="https://kafka-uat.yourcompany.com/kafka/v3/clusters/<cluster_id>/consumer-groups/<consumer_group_id>/lags"
export KADEK_LAG_AUTH_HEADER="Bearer <your-token>"
source .env.session
K6_QUICK=1 K6_VUS=50 k6 run k6/place-cancel-rate-limit.js
```

**Expected response shape:** the script parses JSON and supports:

- **Confluent-style:** `{ "data": [ { "topic_name": "...", "partition_id": 0, "lag": 123 }, ... ] }`
- **Generic:** `{ "lags": [ { "topic": "...", "partition": 0, "lag": 123 } ] }` or array at root with `topic`/`topic_name`, `partition`/`partition_id`, and `lag` (or `log_end_offset - current_offset`).

The report will include a **"Kadek UAT consumer lag (from API)"** section with per-topic/partition lag. If your Kadek UAT API uses a different path or response format, use the full URL for `KADEK_LAG_URL`; if the format does not match, the raw response (truncated) is still included. Get the exact URL and auth from your Kadek/Kafka UAT admin or API docs.

## What it does

Each iteration:

1. POST sig-server `/sign-order` (salt, price, quantity, marketId, maker, signer).
2. POST API `/api/v1/order/{marketId}/place` with the signature.
3. DELETE API `/api/v1/order/{marketId}/cancel` with the returned `order_id`.

So each iteration = 1 sign + 1 place + 1 cancel. Rate limiting (429, 503, or other throttling) will show in k6’s default summary and in `k6-place-cancel-summary.json`.

## Output

- k6 prints the default summary (request count, duration, failure rate, etc.).
- A **Place / Cancel summary** block shows: place/cancel hits, RPS, latency (avg, p95), and **failure breakdown** (sign / place / cancel counts and failure-by-status when available).
- `k6-place-cancel-summary.json` is written in the current directory with full metrics.

Failure breakdown explains what failed and why:

- **Sign**: non-200 from sig-server (e.g. sig-server down or overloaded).
- **Place**: non-2xx from place API (e.g. 429 rate limit, 401 auth, 5xx).
- **Cancel**: non-2xx from cancel API (e.g. 429, 404, 5xx).

When the script can read tagged metrics, it prints place/cancel failures by status (e.g. 429=50, 401=2). Otherwise check `k6-place-cancel-summary.json` for `place_failure_by_status` and `cancel_failure_by_status`.

After each run, `k6-failure-report.txt` is written with the summary and run config; use it when reporting to the API owner (see below).

## Why am I not reaching 20 RPS? (limit is 20 but I see 16 place / 4 cancel)

If the **configured rate limit is 20 RPS** but you only see ~16 place RPS and ~4 cancel RPS:

- **Place at 16 instead of 20**: Either (1) the client is not sending enough attempts per second (try more VUs or lower `K6_ITER_SLEEP`), or (2) the backend cannot keep up (e.g. **consumer lag** on one consumer). If one consumer is lagging, the API may accept requests but effective throughput stays below the limit; report consumer lag to the service owner.
- **Cancel at 4**: Cancel runs only after a successful place. So cancel RPS is capped by place success rate; if many place or cancel calls fail, successful cancel RPS stays low (e.g. 4).

When reporting, include **consumer lag / kadek details** so the owner can correlate: set `K6_CONSUMER_LAG` or `K6_REPORT_EXTRA` when running (see below); they are written into `k6-failure-report.txt`.

If you see **high failure counts** (e.g. 3600 of 4604 place attempts failed): the API is rejecting with 429/503. Then the service must raise limits or scale. Increasing VUs only increases attempted RPS; successful RPS stays low until the backend allows it.

## Reporting to the service owner

Share these so they can see load, failure rate, and status codes:

1. **k6-failure-report.txt** – run config (VUs, duration), Place/Cancel summary, and short interpretation.
2. **k6-place-cancel-summary.json** – full k6 metrics (optional; useful for status-code breakdown and thresholds).

You can say:

- "Load test: place order + cancel order loop, N VUs, 1 minute. Place RPS = X, Cancel RPS = Y (configured limit 20 RPS). Place failures = A of B attempts, Cancel failures = C of D attempts. Failure by status: place 429=..., 503=...; cancel 429=..., etc. [If applicable:] Consumer lag / kadek details attached in the report. Please review rate limits, capacity, and consumer lag."
- Point to **429** as rate limiting, **503** as overload, and **consumer lag** if you see below-limit RPS (e.g. 16 instead of 20).
