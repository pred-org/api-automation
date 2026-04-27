/**
 * k6 load test: place order then cancel order. Used to find API rate limits.
 * No PnL or balance checks; only place + cancel.
 *
 * Prereqs:
 * - Sig-server running (for /sign-order).
 * - Real session: set env ACCESS_TOKEN, REFRESH_COOKIE, EOA, PROXY, USER_ID with real values.
 *   Placeholder values ("...", "0x...") cause 401 on place-order and no orders on the frontend.
 *
 * Run (after auth, recommended):
 *   source .env.session
 *   k6 run tools/k6/place-cancel-rate-limit.js
 *
 * Or set env manually:  export ACCESS_TOKEN="..."; export REFRESH_COOKIE="..."; export EOA="0x..."; export PROXY="0x..."; export USER_ID="<uuid>"
 *
 * 1-minute smoke test:  source .env.session && K6_QUICK=1 k6 run tools/k6/place-cancel-rate-limit.js
 * Higher load (>20 RPS):  source .env.session && K6_QUICK=1 K6_VUS=50 k6 run tools/k6/place-cancel-rate-limit.js
 * Optional:  K6_ITER_SLEEP=0.1  (default 0.2) for less pause between place+cancel cycles.
 * Smoke (quick sanity check):  K6_MODE=smoke K6_VUS=10 k6 run tools/k6/place-cancel-rate-limit.js
 * Load  (ramp up, find limit):  K6_MODE=load k6 run tools/k6/place-cancel-rate-limit.js
 * Spike (burst, match kick-off): K6_MODE=spike k6 run tools/k6/place-cancel-rate-limit.js
 * Place then cancel burst (place 10s to build ~200 order IDs, then cancel all at 30 RPS):
 *   K6_MODE=cancel_burst k6 run tools/k6/place-cancel-rate-limit.js
 *   Env: K6_PLACE_PHASE_SEC=10, K6_CANCEL_BURST_RPS=30, K6_CANCEL_BURST_VUS=30
 * Place only for 1 min (no cancel):  K6_MODE=place_only k6 run tools/k6/place-cancel-rate-limit.js
 * Cancel only for 1 min (setup places to build order IDs, then 1 min cancel at target RPS):  K6_MODE=cancel_only k6 run tools/k6/place-cancel-rate-limit.js
 * Max cancel RPS:  K6_MODE=cancel_only K6_CANCEL_BURST_VUS=50 K6_CANCEL_BURST_RPS=50 K6_CANCEL_ONLY_PLACE_SEC=120 k6 run tools/k6/place-cancel-rate-limit.js
 * Try 25 RPS (even pacing, may get more success than 30/s burst):  K6_CANCEL_BURST_RPS=25 K6_CANCEL_BURST_VUS=25 K6_MODE=cancel_only k6 run ...
 *   (ensure order IDs >= RPS * 60: increase K6_CANCEL_ONLY_PLACE_SEC if cancels run out)
 * If cancel_only hits ~400 cancels (k6 setup payload limit): use a file. Create a JSON array of order IDs, then:
 *   node tools/scripts/place-only-to-file.js   (prints the exact K6_ORDER_IDS_FILE=... command to run)
 *   K6_ORDER_IDS_FILE=<absolute-path> K6_MODE=cancel_only k6 run tools/k6/place-cancel-rate-limit.js
 *   (k6 resolves K6_ORDER_IDS_FILE relative to the script dir; use the absolute path printed by place-only-to-file.js.)
 * Two users (place both sides to get matches -> positions): set USER_2_ACCESS_TOKEN, USER_2_EOA, USER_2_PROXY, USER_2_USER_ID (optional USER_2_REFRESH_COOKIE). User 1 = long, User 2 = short by default (K6_USER_1_SIDE, K6_USER_2_SIDE to override).
 * Include consumer lag / kadek in report:  K6_CONSUMER_LAG="..."  or  K6_REPORT_EXTRA="..."
 * Fetch lag from Kadek UAT API:  KADEK_LAG_URL="https://kadek-uat.../api/consumer-groups/.../lags"  (optional KADEK_LAG_AUTH_HEADER="Bearer ...")
 *
 * After the run, a "Place / Cancel summary" is printed with: place/cancel hits, RPS, latency,
 * failure counts (sign / place / cancel) and failure-by-status when available.
 * Full metrics are written to k6-place-cancel-summary.json.
 */

import http from "k6/http";
import { check, sleep } from "k6";
import { Counter, Trend } from "k6/metrics";

const placeOrderHits = new Counter("place_order_hits");
const cancelOrderHits = new Counter("cancel_order_hits");
const placeOrderLatency = new Trend("place_order_latency_ms");
const cancelOrderLatency = new Trend("cancel_order_latency_ms");
const signOrderFailed = new Counter("sign_order_failed");
const placeOrderFailed = new Counter("place_order_failed");
const cancelOrderFailed = new Counter("cancel_order_failed");
const placeFailureByStatus = new Counter("place_failure_by_status");
const cancelFailureByStatus = new Counter("cancel_failure_by_status");
const placeFail429 = new Counter("place_fail_429");
const placeFail503 = new Counter("place_fail_503");
const placeFailOther = new Counter("place_fail_other");
const cancelFail429 = new Counter("cancel_fail_429");
const cancelFail503 = new Counter("cancel_fail_503");
const cancelFailOther = new Counter("cancel_fail_other");
const placeOrderAttempts = new Counter("place_order_attempts");
const cancelOrderAttempts = new Counter("cancel_order_attempts");

const BASE_URL = __ENV.BASE_URL || "https://uat-frankfurt.pred.app";
const SIG_SERVER_URL = __ENV.SIG_SERVER_URL || "http://localhost:5050";
const MARKET_ID = __ENV.MARKET_ID || "0xf83d64fbb43a9b199109a96fee6291fc66b9fe0a5cd38b0bd2901fd10d7f1900";
const TOKEN_ID = __ENV.TOKEN_ID || "0x1234567890abcdef1234567890abcdef12345678";
const PRICE = "30";
const QTY = "100";

const mode = __ENV.K6_MODE || ((__ENV.K6_QUICK === "1" || __ENV.K6_QUICK === "true") ? "smoke" : "ramp");
const vus = Math.max(1, parseInt(__ENV.K6_VUS || "20", 10));
const defaultSleep = { smoke: 0.1, load: 0.1, spike: 0.05, cancel_burst: 0.2, cancel_only: 0.2, place_only: 0.1 };
const iterSleep = parseFloat(__ENV.K6_ITER_SLEEP || defaultSleep[mode] || 0.2);

const cancelBurstVUs = Math.max(1, parseInt(__ENV.K6_CANCEL_BURST_VUS || "30", 10));
const cancelBurstRPS = Math.max(1, parseInt(__ENV.K6_CANCEL_BURST_RPS || "30", 10));
const placePhaseSec = Math.max(1, parseInt(__ENV.K6_PLACE_PHASE_SEC || "10", 10));
const cancelOnlyPlaceSec = Math.max(1, parseInt(__ENV.K6_CANCEL_ONLY_PLACE_SEC || "120", 10));
const ORDER_IDS_CHUNK_SIZE = 100;

const user1Side = (__ENV.K6_USER_1_SIDE || "long").toLowerCase();
const user2Side = (__ENV.K6_USER_2_SIDE || "short").toLowerCase();
const twoUserMode = !!(__ENV.USER_2_ACCESS_TOKEN && __ENV.USER_2_EOA && __ENV.USER_2_PROXY && __ENV.USER_2_USER_ID);

// When cancel_only and K6_ORDER_IDS_FILE is set, load order IDs in init to avoid setup payload limits (~400 IDs).
// File must be a JSON array of order ID strings, e.g. ["id1","id2",...]. Create it externally or use a helper script.
let fileOrderIds = [];
if (__ENV.K6_MODE === "cancel_only" && __ENV.K6_ORDER_IDS_FILE) {
  try {
    const raw = open(__ENV.K6_ORDER_IDS_FILE);
    fileOrderIds = JSON.parse(raw);
  } catch (e) {
    throw new Error("K6_ORDER_IDS_FILE read failed: " + e.message);
  }
  if (!Array.isArray(fileOrderIds)) throw new Error("K6_ORDER_IDS_FILE must be a JSON array of order ID strings");
}

const stages = {

  // Smoke: quick sanity check after a deploy. Short run, low VUs.
  // Run: K6_MODE=smoke K6_VUS=10 k6 run tools/k6/place-cancel-rate-limit.js
  // Or:  K6_QUICK=1 K6_VUS=10 k6 run tools/k6/place-cancel-rate-limit.js
  smoke: [
    { duration: "10s", target: vus },
    { duration: "50s", target: vus },
  ],

  // Load: gradually ramp up to find the exact point where 429s start.
  // Run: K6_MODE=load k6 run tools/k6/place-cancel-rate-limit.js
  load: [
    { duration: "30s", target: 5 },
    { duration: "30s", target: 10 },
    { duration: "60s", target: 20 },
    { duration: "60s", target: 50 },
    { duration: "60s", target: 100 },
    { duration: "30s", target: 0 },
  ],

  // Spike: sudden burst to simulate match kick-off (many users at once).
  // Run: K6_MODE=spike k6 run tools/k6/place-cancel-rate-limit.js
  spike: [
    { duration: "10s", target: 5 },
    { duration: "10s", target: 100 },
    { duration: "30s", target: 100 },
    { duration: "10s", target: 5 },
    { duration: "30s", target: 5 },
  ],

  // Place for N seconds to build order IDs, then cancel all at target RPS (e.g. 30/s).
  // Run: K6_MODE=cancel_burst k6 run tools/k6/place-cancel-rate-limit.js
  // Env: K6_PLACE_PHASE_SEC=10, K6_CANCEL_BURST_RPS=30, K6_CANCEL_BURST_VUS=30
  cancel_burst: [
    { duration: "10s", target: cancelBurstVUs },
  ],

  // Place order only for 1 min, no cancel.
  // Run: K6_MODE=place_only k6 run tools/k6/place-cancel-rate-limit.js
  place_only: [
    { duration: "60s", target: vus },
  ],

  // Cancel only: short ramp to full VUs then 1 min cancel at target RPS (so N cancels finish in N/RPS seconds).
  // Single stage would ramp 0->VUs over 60s (~half VUs on average); we need full VUs from the start.
  cancel_only: [
    { duration: "2s", target: cancelBurstVUs },
    { duration: "60s", target: cancelBurstVUs },
  ],
};

export const options = {
  stages: stages[mode] || stages.smoke,
  setupTimeout: mode === "cancel_only" && (__ENV.K6_ORDER_IDS_FILE && fileOrderIds.length > 0)
    ? "60s"
    : mode === "cancel_only"
      ? String(cancelOnlyPlaceSec + 30) + "s"
      : "60s",
  thresholds: {
    http_req_failed: ["rate<0.5"],
    http_req_duration: ["p(95)<15000"],
  },
};

export function setup() {
  const accessToken = __ENV.ACCESS_TOKEN;
  const refreshCookie = __ENV.REFRESH_COOKIE;
  const eoa = __ENV.EOA;
  const proxy = __ENV.PROXY;
  const userId = __ENV.USER_ID;

  if (!accessToken || !eoa || !proxy || !userId) {
    throw new Error(
      "Set ACCESS_TOKEN, EOA, PROXY, USER_ID (and REFRESH_COOKIE). Run auth once, then source .env.session"
    );
  }

  const cookie = refreshCookie || "";
  function buildPlaceHeaders(accessTok, refCookie, eoaAddr, proxyAddr) {
    const h = {
      "Content-Type": "application/json",
      "Authorization": "Bearer " + accessTok,
      "X-Wallet-Address": eoaAddr,
      "X-Proxy-Address": proxyAddr,
    };
    if (refCookie) h["Cookie"] = refCookie;
    return h;
  }
  const placeHeaders = buildPlaceHeaders(accessToken, cookie, eoa, proxy);
  const user1 = { placeHeaders, userId, eoa, proxy, side: user1Side };

  let user2 = null;
  if (twoUserMode) {
    const ref2 = __ENV.USER_2_REFRESH_COOKIE || "";
    user2 = {
      placeHeaders: buildPlaceHeaders(__ENV.USER_2_ACCESS_TOKEN, ref2, __ENV.USER_2_EOA, __ENV.USER_2_PROXY),
      userId: __ENV.USER_2_USER_ID,
      eoa: __ENV.USER_2_EOA,
      proxy: __ENV.USER_2_PROXY,
      side: user2Side,
    };
  }

  if (mode === "cancel_only" && __ENV.K6_ORDER_IDS_FILE && fileOrderIds.length > 0) {
    return {
      useFileOrderIds: true,
      totalOrderIds: fileOrderIds.length,
      placeHeaders,
      MARKET_ID,
      BASE_URL,
    };
  }

  if (mode === "cancel_burst" || mode === "cancel_only") {
    const orderIds = [];
    const placeSec = mode === "cancel_only" ? cancelOnlyPlaceSec : placePhaseSec;
    const placeEnd = Date.now() + placeSec * 1000;
    while (Date.now() < placeEnd) {
      const salt = String(Date.now()) + String(Math.floor(Math.random() * 10000000));
      const signBody = JSON.stringify({
        salt,
        price: PRICE,
        quantity: QTY,
        questionId: MARKET_ID,
        feeRateBps: 0,
        intent: 0,
        maker: proxy,
        signer: eoa,
        priceInCents: false,
      });
      const signRes = http.post(
        `${SIG_SERVER_URL}/sign-order`,
        signBody,
        { headers: { "Content-Type": "application/json" } }
      );
      if (signRes.status !== 200) continue;
      let sig;
      try {
        sig = JSON.parse(signRes.body).signature;
      } catch (e) {
        continue;
      }
      if (!sig) continue;
      const timestamp = Math.floor(Date.now() / 1000);
      const amount = String(Math.floor((Number(PRICE) * Number(QTY)) / 100));
      const placeBody = JSON.stringify({
        salt,
        user_id: userId,
        market_id: MARKET_ID,
        side: "long",
        token_id: TOKEN_ID,
        price: PRICE,
        quantity: QTY,
        amount,
        is_low_priority: false,
        signature: sig,
        type: "limit",
        timestamp,
        reduce_only: false,
        fee_rate_bps: 0,
      });
      const placeRes = http.post(
        `${BASE_URL}/api/v1/order/${MARKET_ID}/place`,
        placeBody,
        { headers: placeHeaders }
      );
      if (placeRes.status >= 200 && placeRes.status < 300) {
        try {
          const oid = JSON.parse(placeRes.body).order_id;
          if (oid) orderIds.push(oid);
        } catch (e) {}
      }
      sleep(0.05);
    }
    const result = { totalOrderIds: orderIds.length, placeHeaders, MARKET_ID, BASE_URL };
    for (let c = 0; c < orderIds.length; c += ORDER_IDS_CHUNK_SIZE) {
      result["orderIds_" + (c / ORDER_IDS_CHUNK_SIZE)] = orderIds.slice(c, c + ORDER_IDS_CHUNK_SIZE);
    }
    return result;
  }

  const salt = String(Date.now());
  const signBody = JSON.stringify({
    salt,
    price: PRICE,
    quantity: QTY,
    questionId: MARKET_ID,
    feeRateBps: 0,
    intent: 0,
    maker: proxy,
    signer: eoa,
    priceInCents: false,
  });

  const signRes = http.post(
    `${SIG_SERVER_URL}/sign-order`,
    signBody,
    { headers: { "Content-Type": "application/json" }, tags: { name: "setup-sign" } }
  );
  if (signRes.status !== 200) {
    throw new Error(
      `sig-server sign-order failed: ${signRes.status} at ${SIG_SERVER_URL}. Is sig-server running? Body: ${signRes.body?.slice(0, 200) || "(none)"}`
    );
  }
  let sig;
  try {
    sig = JSON.parse(signRes.body).signature;
  } catch (e) {
    throw new Error(`sig-server did not return JSON with signature. Body: ${signRes.body?.slice(0, 200)}`);
  }
  if (!sig) throw new Error("sig-server returned empty signature");

  const timestamp = Math.floor(Date.now() / 1000);
  const amount = String(Math.floor((Number(PRICE) * Number(QTY)) / 100));
  const placeBody = JSON.stringify({
    salt,
    user_id: userId,
    market_id: MARKET_ID,
    side: "long",
    token_id: TOKEN_ID,
    price: PRICE,
    quantity: QTY,
    amount,
    is_low_priority: false,
    signature: sig,
    type: "limit",
    timestamp,
    reduce_only: false,
    fee_rate_bps: 0,
  });

  const placeRes = http.post(
    `${BASE_URL}/api/v1/order/${MARKET_ID}/place`,
    placeBody,
    { headers: placeHeaders, tags: { name: "setup-place" } }
  );
  if (placeRes.status < 200 || placeRes.status >= 300) {
    const body = placeRes.body?.slice(0, 300) || "(none)";
    const hint = placeRes.status === 401 || placeRes.status === 403
      ? "Token expired or invalid? Re-run: mvn test -Dtest=AuthFlowTest then source .env.session"
      : placeRes.status >= 500
        ? "Server unavailable (5xx). Try again later or check BASE_URL."
        : "Check request and BASE_URL.";
    throw new Error(`place-order failed: ${placeRes.status}. ${hint} Body: ${body}`);
  }
  let orderId;
  try {
    orderId = JSON.parse(placeRes.body).order_id;
  } catch (e) {
    throw new Error(`place-order response missing order_id. Body: ${placeRes.body?.slice(0, 200)}`);
  }
  if (!orderId) throw new Error("place-order response had empty order_id");

  const cancelBody = JSON.stringify({ order_id: orderId, market_id: MARKET_ID });
  const cancelRes = http.del(
    `${BASE_URL}/api/v1/order/${MARKET_ID}/cancel`,
    cancelBody,
    { headers: placeHeaders, tags: { name: "setup-cancel" } }
  );
  if (cancelRes.status < 200 || cancelRes.status >= 300) {
    throw new Error(`cancel-order failed: ${cancelRes.status}. Body: ${cancelRes.body?.slice(0, 200) || "(none)"}`);
  }

  const out = { accessToken, refreshCookie: cookie, eoa, proxy, userId };
  if (twoUserMode && user2) {
    out.users = [user1, user2];
    out.twoUser = true;
  }
  return out;
}

export default function (data) {
  const totalOrderIds = data.totalOrderIds != null ? data.totalOrderIds : (data.orderIds ? data.orderIds.length : 0);
  const getOrderId = function (index) {
    if (data.useFileOrderIds && fileOrderIds && index >= 0 && index < fileOrderIds.length) return fileOrderIds[index];
    if (data.totalOrderIds != null && data.totalOrderIds > 0) {
      const chunkIndex = Math.floor(index / ORDER_IDS_CHUNK_SIZE);
      const chunk = data["orderIds_" + chunkIndex];
      if (!chunk) return null;
      const innerIndex = index % ORDER_IDS_CHUNK_SIZE;
      if (innerIndex >= chunk.length) return null;
      return chunk[innerIndex];
    }
    if (data.orderIds && data.orderIds.length > 0 && index < data.orderIds.length) return data.orderIds[index];
    return null;
  };

  if (totalOrderIds > 0) {
    if (__ITER === 0 && __VU === 1) {
      console.log("cancel phase: totalOrderIds=" + totalOrderIds + " (expect this many cancels possible in 60s at target RPS)");
    }
    const index = __ITER * cancelBurstVUs + (__VU - 1);
    if (index >= totalOrderIds) {
      const cancelSleep = Math.max(0.01, cancelBurstVUs / cancelBurstRPS - 0.2);
      sleep(cancelSleep);
      return;
    }
    const orderId = getOrderId(index);
    if (!orderId) {
      const cancelSleep = Math.max(0.01, cancelBurstVUs / cancelBurstRPS - 0.2);
      sleep(cancelSleep);
      return;
    }
    cancelOrderAttempts.add(1);
    const cancelBody = JSON.stringify({
      order_id: orderId,
      market_id: data.MARKET_ID,
    });
    const cancelRes = http.del(
      `${data.BASE_URL}/api/v1/order/${data.MARKET_ID}/cancel`,
      cancelBody,
      { headers: data.placeHeaders, tags: { name: "cancel-order" } }
    );
    cancelOrderLatency.add(cancelRes.timings.duration);
    if (cancelRes.status >= 200 && cancelRes.status < 300) {
      cancelOrderHits.add(1);
    } else {
      cancelOrderFailed.add(1);
      cancelFailureByStatus.add(1, { status: String(cancelRes.status) });
      if (cancelRes.status === 429) cancelFail429.add(1);
      else if (cancelRes.status === 503) cancelFail503.add(1);
      else cancelFailOther.add(1);
      console.log("CANCEL_FAIL status=" + cancelRes.status + " body=" + (cancelRes.body ? cancelRes.body.slice(0, 150) : ""));
    }
    check(cancelRes, {
      "cancel-order 2xx": (r) => r.status >= 200 && r.status < 300,
    });
    const targetInterval = cancelBurstVUs / cancelBurstRPS;
    const latencyEstimateSec = parseFloat(__ENV.K6_CANCEL_LATENCY_ESTIMATE_MS || "200", 10) / 1000;
    const cancelSleep = Math.max(0.01, targetInterval - latencyEstimateSec);
    sleep(cancelSleep);
    return;
  }

  if (data.twoUser && __ITER === 0 && __VU === 1) {
    console.log("two-user mode: user1=" + (data.users[0].side) + ", user2=" + (data.users[1].side) + " (orders may match -> positions)");
  }
  const currentUser = data.twoUser && data.users && data.users.length >= 2
    ? data.users[__ITER % 2]
    : {
        placeHeaders: {
          "Content-Type": "application/json",
          "Authorization": "Bearer " + data.accessToken,
          "X-Wallet-Address": data.eoa,
          "X-Proxy-Address": data.proxy,
          ...(data.refreshCookie ? { "Cookie": data.refreshCookie } : {}),
        },
        userId: data.userId,
        eoa: data.eoa,
        proxy: data.proxy,
        side: user1Side,
      };
  const { placeHeaders: userPlaceHeaders, userId, eoa, proxy, side } = currentUser;

  const salt = String(Date.now()) + String(Math.floor(Math.random() * 10000000));
  const timestamp = Math.floor(Date.now() / 1000);

  const signOrderBody = JSON.stringify({
    salt,
    price: PRICE,
    quantity: QTY,
    questionId: MARKET_ID,
    feeRateBps: 0,
    intent: 0,
    maker: proxy,
    signer: eoa,
    priceInCents: false,
  });

  const signRes = http.post(
    `${SIG_SERVER_URL}/sign-order`,
    signOrderBody,
    { headers: { "Content-Type": "application/json" }, tags: { name: "sign-order" } }
  );

  const signOk = check(signRes, {
    "sign-order status 200": (r) => r.status === 200,
  });
  if (!signOk) {
    signOrderFailed.add(1);
    return;
  }

  let sig;
  try {
    sig = JSON.parse(signRes.body).signature;
  } catch (e) {
    return;
  }
  if (!sig) return;

  const amount = String(Math.floor((Number(PRICE) * Number(QTY)) / 100));
  const placeBody = JSON.stringify({
    salt,
    user_id: userId,
    market_id: MARKET_ID,
    side,
    token_id: TOKEN_ID,
    price: PRICE,
    quantity: QTY,
    amount,
    is_low_priority: false,
    signature: sig,
    type: "limit",
    timestamp,
    reduce_only: false,
    fee_rate_bps: 0,
  });

  placeOrderAttempts.add(1);
  const placeRes = http.post(
    `${BASE_URL}/api/v1/order/${MARKET_ID}/place`,
    placeBody,
    { headers: userPlaceHeaders, tags: { name: "place-order" } }
  );
  placeOrderLatency.add(placeRes.timings.duration);
  if (placeRes.status >= 200 && placeRes.status < 300) {
    placeOrderHits.add(1);
  } else {
    placeOrderFailed.add(1);
    placeFailureByStatus.add(1, { status: String(placeRes.status) });
    if (placeRes.status === 429) placeFail429.add(1);
    else if (placeRes.status === 503) placeFail503.add(1);
    else placeFailOther.add(1);
    console.log("PLACE_FAIL status=" + placeRes.status + " body=" + (placeRes.body ? placeRes.body.slice(0, 150) : ""));
  }

  check(placeRes, {
    "place-order 2xx": (r) => r.status >= 200 && r.status < 300,
  });

  if (mode === "place_only") {
    sleep(iterSleep);
    return;
  }

  let orderId;
  try {
    orderId = JSON.parse(placeRes.body).order_id;
  } catch (e) {
    sleep(0.5);
    return;
  }
  if (!orderId) {
    sleep(0.5);
    return;
  }

  const cancelBody = JSON.stringify({
    order_id: orderId,
    market_id: MARKET_ID,
  });

  cancelOrderAttempts.add(1);
  const cancelRes = http.del(
    `${BASE_URL}/api/v1/order/${MARKET_ID}/cancel`,
    cancelBody,
    { headers: userPlaceHeaders, tags: { name: "cancel-order" } }
  );
  cancelOrderLatency.add(cancelRes.timings.duration);
  if (cancelRes.status >= 200 && cancelRes.status < 300) {
    cancelOrderHits.add(1);
  } else {
    cancelOrderFailed.add(1);
    cancelFailureByStatus.add(1, { status: String(cancelRes.status) });
    if (cancelRes.status === 429) cancelFail429.add(1);
    else if (cancelRes.status === 503) cancelFail503.add(1);
    else cancelFailOther.add(1);
    console.log("CANCEL_FAIL status=" + cancelRes.status + " body=" + (cancelRes.body ? cancelRes.body.slice(0, 150) : ""));
  }

  check(cancelRes, {
    "cancel-order 2xx": (r) => r.status >= 200 && r.status < 300,
  });

  sleep(iterSleep);
}

function fmt(val) {
  if (val == null || Number.isNaN(val)) return "N/A";
  if (typeof val === "number") return Number.isInteger(val) ? String(val) : val.toFixed(2);
  return String(val);
}

function formatKadekLagResponse(body) {
  let json;
  try {
    json = JSON.parse(body);
  } catch (e) {
    return body.trim().slice(0, 500);
  }
  if (!json || typeof json !== "object") return "";

  const parts = [];
  const list = json.data || json.lags || json.consumer_lags || (Array.isArray(json) ? json : null);
  if (Array.isArray(list)) {
    for (const item of list) {
      const topic = item.topic_name || item.topic;
      const partition = item.partition_id != null ? item.partition_id : item.partition;
      const lag = item.lag != null ? item.lag : (item.log_end_offset != null && item.current_offset != null ? item.log_end_offset - item.current_offset : null);
      if (topic != null && partition != null && lag != null) parts.push(topic + "/p" + partition + " lag " + lag);
    }
  }
  if (parts.length) return parts.join("; ");
  if (json.lag != null) return "lag " + json.lag;
  return JSON.stringify(json).slice(0, 400);
}

export function handleSummary(data) {
  const m = data.metrics || {};
  const iters = m.iterations && m.iterations.values ? m.iterations.values.count : 0;
  const placeCount = m.place_order_hits && m.place_order_hits.values ? m.place_order_hits.values.count : 0;
  const cancelCount = m.cancel_order_hits && m.cancel_order_hits.values ? m.cancel_order_hits.values.count : 0;
  const durationSec = (m.iteration_duration && m.iteration_duration.values && m.iteration_duration.values.avg)
    ? (m.iteration_duration.values.avg / 1e6) * (iters || 1) : 0;
  const testDuration = data.state ? (data.state.testRunDurationMs || 0) / 1000 : durationSec;

  const placeLat = m.place_order_latency_ms && m.place_order_latency_ms.values ? m.place_order_latency_ms.values : {};
  const cancelLat = m.cancel_order_latency_ms && m.cancel_order_latency_ms.values ? m.cancel_order_latency_ms.values : {};
  const httpReqs = m.http_reqs && m.http_reqs.values ? m.http_reqs.values : {};
  const signFailed = m.sign_order_failed && m.sign_order_failed.values ? m.sign_order_failed.values.count : 0;
  const placeFailed = m.place_order_failed && m.place_order_failed.values ? m.place_order_failed.values.count : 0;
  const cancelFailed = m.cancel_order_failed && m.cancel_order_failed.values ? m.cancel_order_failed.values.count : 0;
  const placeAttemptsVal = m.place_order_attempts && m.place_order_attempts.values ? m.place_order_attempts.values.count : null;
  const cancelAttemptsVal = m.cancel_order_attempts && m.cancel_order_attempts.values ? m.cancel_order_attempts.values.count : null;
  const placeAttempts = placeAttemptsVal != null ? placeAttemptsVal : iters;
  const cancelAttempts = cancelAttemptsVal != null ? cancelAttemptsVal : placeCount;

  const httpFailed = m.http_req_failed && m.http_req_failed.values ? m.http_req_failed.values : {};
  const failedPct = httpFailed.rate != null ? httpFailed.rate * 100 : (httpFailed.passes != null && httpFailed.fails != null && (httpFailed.passes + httpFailed.fails) > 0
    ? (100 * httpFailed.fails / (httpFailed.passes + httpFailed.fails)) : null);

  const reportMode = __ENV.K6_MODE || "";
  const phaseDurationSec = reportMode === "cancel_only" ? 60 : (reportMode === "cancel_burst" ? 10 : (reportMode === "place_only" ? 60 : null));
  const placeDuration = reportMode === "place_only" && phaseDurationSec ? phaseDurationSec : testDuration;
  const targetCancelRps = Math.max(1, parseInt(__ENV.K6_CANCEL_BURST_RPS || "30", 10));
  const cancelActiveDurationSec = (reportMode === "cancel_only" || reportMode === "cancel_burst") && cancelAttempts > 0
    ? cancelAttempts / targetCancelRps
    : 0;
  const cancelDuration = (reportMode === "cancel_only" || reportMode === "cancel_burst") && phaseDurationSec ? phaseDurationSec : testDuration;
  const placeRps = placeDuration > 0 ? placeCount / placeDuration : 0;
  const cancelRps = cancelActiveDurationSec > 0 ? cancelCount / cancelActiveDurationSec : (cancelDuration > 0 ? cancelCount / cancelDuration : 0);
  const totalRps = httpReqs.rate != null ? httpReqs.rate : (testDuration > 0 ? (placeCount + cancelCount + (iters || 0)) / testDuration : 0);

  function getFailureByStatusFromSubmetrics(metricPrefix) {
    const parts = [];
    for (const [key, metric] of Object.entries(m)) {
      if (!key.startsWith(metricPrefix) || key === metricPrefix || !metric?.values?.count) continue;
      const match = key.match(/\{status:(\d+)\}/) || key.match(/status[=:](\d+)/);
      const status = match ? match[1] : key.replace(metricPrefix, "").replace(/[{}]/g, "");
      if (status && metric.values.count > 0) parts.push(status + "=" + metric.values.count);
    }
    return parts.length ? parts.sort().join(", ") : "";
  }
  const placeByStatus = getFailureByStatusFromSubmetrics("place_failure_by_status");
  const cancelByStatus = getFailureByStatusFromSubmetrics("cancel_failure_by_status");

  const p429 = (m.place_fail_429 && m.place_fail_429.values && m.place_fail_429.values.count) || 0;
  const p503 = (m.place_fail_503 && m.place_fail_503.values && m.place_fail_503.values.count) || 0;
  const pOther = (m.place_fail_other && m.place_fail_other.values && m.place_fail_other.values.count) || 0;
  const c429 = (m.cancel_fail_429 && m.cancel_fail_429.values && m.cancel_fail_429.values.count) || 0;
  const c503 = (m.cancel_fail_503 && m.cancel_fail_503.values && m.cancel_fail_503.values.count) || 0;
  const cOther = (m.cancel_fail_other && m.cancel_fail_other.values && m.cancel_fail_other.values.count) || 0;
  const placeReasons = ["429=" + p429, "503=" + p503, "other=" + pOther].filter(function (s) { return parseInt(s.split("=")[1], 10) > 0; }).join(", ");
  const cancelReasons = ["429=" + c429, "503=" + c503, "other=" + cOther].filter(function (s) { return parseInt(s.split("=")[1], 10) > 0; }).join(", ");

  const iterLabel = reportMode === "place_only" ? "Iterations (place-only):" : (reportMode === "cancel_only" || reportMode === "cancel_burst" ? "Iterations (cancel phase):" : "Iterations (full place+cancel cycles):");

  const lines = [
    "",
    "========== Place / Cancel summary ==========",
    "  " + iterLabel + " " + fmt(iters),
  ];
  if (reportMode === "cancel_only" || reportMode === "cancel_burst") {
    lines.push("  Target cancel RPS: " + (__ENV.K6_CANCEL_BURST_RPS || "30") + " (set K6_CANCEL_BURST_RPS=30 for 30 RPS)");
    if (cancelAttempts > 0) {
      lines.push("  Cancel active window: " + fmt(cancelActiveDurationSec) + " s (" + fmt(cancelAttempts) + " attempts at " + targetCancelRps + " target RPS; success RPS above is over this window)");
      if (cancelAttempts < targetCancelRps * (reportMode === "cancel_only" ? 55 : 8)) {
        lines.push("  Tip: to sustain target RPS for 60s use 1800+ order IDs: node tools/scripts/place-only-to-file.js then K6_ORDER_IDS_FILE=<path>");
      }
    }
  }
  lines.push(
    "  Place order:  hits = " + fmt(placeCount) + ",  RPS = " + fmt(placeRps) + ",  latency avg = " + fmt(placeLat.avg) + " ms,  p95 = " + fmt(placeLat["p(95)"]) + " ms",
    "  Cancel order: hits = " + fmt(cancelCount) + ",  RPS = " + fmt(cancelRps) + ",  latency avg = " + fmt(cancelLat.avg) + " ms,  p95 = " + fmt(cancelLat["p(95)"]) + " ms",
    "  Overall:      HTTP RPS = " + fmt(totalRps) + ",  failed = " + fmt(failedPct) + "%",
    "  Test duration: " + fmt(testDuration) + " s",
    "  --- Failures ---",
    "  Sign:   " + fmt(signFailed) + " (non-200 from sig-server)",
    "  Place:  " + fmt(placeFailed) + " of " + fmt(placeAttempts) + " attempts" + (placeByStatus ? "  [by status: " + placeByStatus + "]" : ""),
    "  Cancel: " + fmt(cancelFailed) + " of " + fmt(cancelAttempts) + " attempts" + (cancelByStatus ? "  [by status: " + cancelByStatus + "]" : ""),
    "  Failure reasons:  Place (" + (placeReasons || "none") + ")  Cancel (" + (cancelReasons || "none") + ")",
    "============================================",
    ""
  );
  const summaryText = lines.join("\n");

  const k6Vus = __ENV.K6_VUS || "20";
  const k6Sleep = __ENV.K6_ITER_SLEEP || "0.2";
  const consumerLag = __ENV.K6_CONSUMER_LAG || "";
  const reportExtra = __ENV.K6_REPORT_EXTRA || "";

  const runConfigParts = ["K6_MODE=" + (__ENV.K6_MODE || "smoke") + ",  K6_VUS=" + k6Vus + ",  K6_ITER_SLEEP=" + k6Sleep + " s,  duration=" + fmt(testDuration) + " s"];
  if (reportMode === "cancel_only" || reportMode === "cancel_burst") {
    runConfigParts.push("K6_CANCEL_BURST_RPS=" + (__ENV.K6_CANCEL_BURST_RPS || "30") + ",  K6_CANCEL_BURST_VUS=" + (__ENV.K6_CANCEL_BURST_VUS || "30"));
  }
  const runConfigLine = "Run config:  " + runConfigParts.join(",  ");

  let kadekLagSection = "";
  const kadekLagUrl = __ENV.KADEK_LAG_URL || __ENV.KADEK_UAT_LAG_URL || "";
  if (kadekLagUrl) {
    const headers = {};
    if (__ENV.KADEK_LAG_AUTH_HEADER) headers["Authorization"] = __ENV.KADEK_LAG_AUTH_HEADER;
    try {
      const res = http.get(kadekLagUrl, { headers, timeout: "10s" });
      if (res.status === 200 && res.body) {
        kadekLagSection = formatKadekLagResponse(res.body);
        if (!kadekLagSection) kadekLagSection = "(parse failed) " + res.body.slice(0, 300);
      } else {
        kadekLagSection = "HTTP " + res.status + (res.body ? ": " + res.body.slice(0, 200) : "");
      }
    } catch (e) {
      kadekLagSection = "Fetch error: " + (e.message || String(e));
    }
  }

  const reportLines = [
    "k6 Place/Cancel rate-limit test - report for service owner",
    "==========================================================",
    runConfigLine,
    "Script: tools/k6/place-cancel-rate-limit.js (place order then cancel order per iteration)",
    "",
    summaryText.trim(),
    "",
  ];
  if (kadekLagSection) {
    reportLines.push("Kadek UAT consumer lag (from API):");
    reportLines.push("  " + kadekLagSection.replace(/\n/g, "\n  "));
    reportLines.push("");
  }
  if (consumerLag || reportExtra) {
    reportLines.push("Additional context (from env):");
    if (consumerLag) reportLines.push("  Consumer lag / backend: " + consumerLag);
    if (reportExtra) reportLines.push("  " + reportExtra);
    reportLines.push("");
  }
  reportLines.push(
    "How to interpret:",
    "- Place/Cancel RPS = successful 2xx requests per second. Failures are non-2xx (e.g. 429 rate limit, 503).",
    "- To reach threshold (e.g. 20 RPS): increase K6_VUS and/or set K6_ITER_SLEEP=0.1 (or 0.05) so the client sends more attempts/s; then observed RPS can approach the API limit.",
    "- If configured limit is 20 RPS but observed place RPS is lower (e.g. 16), possible causes: client not sending enough (try more VUs / less K6_ITER_SLEEP), or backend/consumer lag.",
    "- High place/cancel failure % with status 429 = API rate limit; 503 = server overload/unavailable.",
    "- Share this file and k6-place-cancel-summary.json with the API owner for capacity/limit review.",
    ""
  );
  const reportText = reportLines.join("\n");

  return {
    stdout: summaryText,
    "k6-place-cancel-summary.json": JSON.stringify(data, null, 2),
    "k6-failure-report.txt": reportText,
  };
}
