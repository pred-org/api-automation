# k6 Place / Cancel – Command reference

Important commands used in this project, with short descriptions. Run from the project root. Prereqs: sig-server running; real session env (see Auth below).

---

## Auth and session

| Command | Description |
|---------|-------------|
| `source .env.session` | Load session env (ACCESS_TOKEN, REFRESH_COOKIE, EOA, PROXY, USER_ID) so k6 and Node scripts can call the API. Run once per terminal before k6 or place-only-to-file. |
| `mvn test -Dtest=AuthFlowTest` | (Optional) Run auth flow to refresh tokens; then run `source .env.session` to pick up the new .env.session. |

---

## Two users (place both sides so orders can match -> positions)

Use a second wallet/session so user 1 places long and user 2 places short at the same price; when they match you get positions (not just open orders). Enables testing position and PnL flows later.

| Command / env | Description |
|---------------|-------------|
| Set second user env | After auth for the second wallet, set: `USER_2_ACCESS_TOKEN`, `USER_2_EOA`, `USER_2_PROXY`, `USER_2_USER_ID` (optional `USER_2_REFRESH_COOKIE`). Source .env.session for user 1, then export USER_2_* from a second .env or manual export. |
| `source .env.session && export USER_2_ACCESS_TOKEN=... USER_2_EOA=... USER_2_PROXY=... USER_2_USER_ID=... && K6_MODE=smoke K6_VUS=10 k6 run k6/place-cancel-rate-limit.js` | Two-user smoke: even iterations use user 1 (long), odd use user 2 (short). Same price so orders may match. |
| `K6_USER_1_SIDE=long K6_USER_2_SIDE=short` | Default: user 1 = long, user 2 = short. Override if needed. |
| Sig-server | Must be able to sign for both wallets (e.g. sign-order called with maker/signer per user). Configure or run auth for the second wallet so sig-server has the second key. |

---

## Place + Cancel together (one place, then cancel that order)

Each iteration: sign -> place order -> read order_id from response -> cancel that order.

| Command | Description |
|---------|-------------|
| `source .env.session && K6_MODE=smoke K6_VUS=10 k6 run k6/place-cancel-rate-limit.js` | Smoke: short run, 10 VUs, place+cancel per iteration. Quick sanity check. |
| `source .env.session && K6_MODE=load k6 run k6/place-cancel-rate-limit.js` | Load: ramp VUs (5 -> 10 -> 20 -> 50 -> 100) to find where 429s start. |
| `source .env.session && K6_MODE=spike k6 run k6/place-cancel-rate-limit.js` | Spike: burst to 100 VUs to simulate match kick-off. |
| `source .env.session && K6_QUICK=1 K6_VUS=50 k6 run k6/place-cancel-rate-limit.js` | Higher load: 50 VUs, 1-minute run (place+cancel). |
| `K6_ITER_SLEEP=0.1 K6_MODE=smoke k6 run k6/place-cancel-rate-limit.js` | Less pause between place+cancel cycles (default 0.1 for smoke). Use to push place/cancel RPS toward API limit. |

---

## Place only (no cancel)

Place orders for 1 minute; no cancel. Used to build order count or test place rate limit only.

| Command | Description |
|---------|-------------|
| `source .env.session && K6_MODE=place_only k6 run k6/place-cancel-rate-limit.js` | Place only for 60 s. No cancel in the main phase. |

---

## Cancel only (order IDs from setup)

Setup places orders for N seconds to build order IDs; then 1 min cancel phase at target RPS. Limited to ~400 order IDs due to k6 setup payload size.

| Command | Description |
|---------|-------------|
| `source .env.session && K6_MODE=cancel_only k6 run k6/place-cancel-rate-limit.js` | Cancel only: setup places for 120 s (default), then 60 s cancel at 30 RPS. Order IDs come from setup (~400 max). |
| `K6_MODE=cancel_only K6_CANCEL_BURST_RPS=25 K6_CANCEL_BURST_VUS=25 k6 run k6/place-cancel-rate-limit.js` | Cancel at 25 RPS with 25 VUs (even pacing; may get more success than 30/s burst). |
| `K6_MODE=cancel_only K6_CANCEL_BURST_VUS=50 K6_CANCEL_BURST_RPS=50 K6_CANCEL_ONLY_PLACE_SEC=120 k6 run k6/place-cancel-rate-limit.js` | Max cancel RPS: 50 VUs, 50 target RPS. Ensure enough order IDs (RPS * 60); increase K6_CANCEL_ONLY_PLACE_SEC if cancels run out. |

---

## Cancel only (order IDs from file, 1000+ cancels)

Two-step flow when you need more than ~400 cancels: generate order IDs to a file, then run cancel_only with that file.

| Command | Description |
|---------|-------------|
| `source .env.session && node scripts/place-only-to-file.js` | Place orders for 120 s (default) and write order IDs to `order-ids.json` in project root. Prints the absolute path and the exact k6 command to run next. |
| `source .env.session && PLACE_SEC=90 node scripts/place-only-to-file.js` | Same as above but place for 90 s. Use when you want fewer IDs or a shorter run. |
| `PLACE_SEC=120 OUT_FILE=./order-ids.json node scripts/place-only-to-file.js` | Optional: set place duration and output file explicitly (after sourcing .env.session). |
| `K6_ORDER_IDS_FILE=/Users/abhishekmishra/api-automation/order-ids.json K6_MODE=cancel_only k6 run k6/place-cancel-rate-limit.js` | Cancel only using order IDs from the file. Use the absolute path printed by place-only-to-file.js (k6 resolves paths relative to the script dir). |
| `K6_ORDER_IDS_FILE=/Users/abhishekmishra/api-automation/order-ids.json K6_CANCEL_BURST_RPS=25 K6_CANCEL_BURST_VUS=25 K6_MODE=cancel_only k6 run k6/place-cancel-rate-limit.js` | Same as above at 25 RPS. For 60 s at 25 RPS you need at least 1500 order IDs (e.g. PLACE_SEC=90 or more). |

---

## Place then cancel burst (short place phase, then cancel at target RPS)

Place for 10 s to build order IDs, then cancel all at target RPS (e.g. 30/s). Single run; order IDs from setup.

| Command | Description |
|---------|-------------|
| `source .env.session && K6_MODE=cancel_burst k6 run k6/place-cancel-rate-limit.js` | Place for 10 s (default), then cancel at 30 RPS. Env: K6_PLACE_PHASE_SEC=10, K6_CANCEL_BURST_RPS=30, K6_CANCEL_BURST_VUS=30. |

---

## Optional: report and Kadek

| Command / env | Description |
|---------------|-------------|
| `k6-failure-report.txt` | After each run, this file contains the "report for service owner" (run config, summary, how to interpret). Same content as stdout summary plus Run config and interpretation notes. |
| `k6-place-cancel-summary.json` | Full metrics JSON for the run. Share with API owner with k6-failure-report.txt. |
| `K6_CONSUMER_LAG="..."` or `K6_REPORT_EXTRA="..."` | Include extra context in the report. |
| `KADEK_LAG_URL="https://..."` | (Optional) Fetch consumer lag from Kadek UAT API and add to report. Use with `KADEK_LAG_AUTH_HEADER="Bearer ..."` if needed. |

---

## Quick reference: modes

| K6_MODE | What runs |
|---------|-----------|
| `smoke` | Place + cancel per iteration; short run. |
| `load` | Place + cancel; ramp VUs to find limit. |
| `spike` | Place + cancel; burst to 100 VUs. |
| `place_only` | Place only for 60 s; no cancel. |
| `cancel_only` | Setup places to build IDs (or use file); then 60 s cancel at target RPS. |
| `cancel_burst` | Place 10 s then cancel at target RPS. |

With two users set (USER_2_*), smoke/load/spike/place_only alternate user 1 (long) and user 2 (short) so orders can match and create positions.
