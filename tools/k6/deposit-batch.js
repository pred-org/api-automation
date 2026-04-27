import http from "k6/http";
import { check } from "k6";

/*
 * How to run:
 *
 * UAT (default env):
 *   k6 run tools/k6/deposit-batch.js -e USER_ID=<uat-user-id>
 *
 * Testnet:
 *   k6 run tools/k6/deposit-batch.js \
 *     -e K6_ENV=testnet \
 *     -e USER_ID=<testnet-user-id>
 *
 * Custom rate/duration/region:
 *   k6 run tools/k6/deposit-batch.js \
 *     -e USER_ID=<user-id> \
 *     -e K6_RATE=500 \
 *     -e K6_DURATION=1m \
 *     -e K6_REGION=singapore
 *
 * Optional token for step-1 internal deposit:
 *   -e INTERNAL_DEPOSIT_TOKEN=<token>
 *
 * Optional explicit internal URL override (highest priority):
 *   -e API_BASE_URI_INTERNAL=http://api-internal.example.com
 */

const ENV_DEFAULTS = {
  uat: {
    INTERNAL_BASE: "http://api-internal.uat-frankfurt.pred.app",
  },
  testnet: {
    INTERNAL_BASE: "http://api-internal.testnet.pred.app",
  },
};

const K6_ENV = (__ENV.K6_ENV || "uat").toLowerCase();
const envConfig = ENV_DEFAULTS[K6_ENV] || ENV_DEFAULTS.uat;
const INTERNAL_BASE = __ENV.API_BASE_URI_INTERNAL || envConfig.INTERNAL_BASE;

const USER_ID = __ENV.USER_ID || "";
const INTERNAL_DEPOSIT_TOKEN = __ENV.INTERNAL_DEPOSIT_TOKEN || "";

const REGION = __ENV.K6_REGION || "unknown";
const RATE = parseInt(__ENV.K6_RATE || "500", 10);
const DURATION = __ENV.K6_DURATION || "1m";

export const options = {
  scenarios: {
    deposit_batch: {
      executor: "constant-arrival-rate",
      rate: RATE,
      timeUnit: "1m",
      duration: DURATION,
      preAllocatedVUs: parseInt(__ENV.K6_VUS || "20", 10),
      maxVUs: parseInt(__ENV.K6_MAX_VUS || "50", 10),
    },
  },
};

export function setup() {
  console.log(`=== Deposit Batch | Env: ${K6_ENV} | Region: ${REGION} | Rate: ${RATE}/min | Duration: ${DURATION} ===`);
  console.log(`=== Target: ${INTERNAL_BASE} | User: ${USER_ID} ===`);
  return null;
}

function extractTransactionHash(bodyText) {
  try {
    const body = JSON.parse(bodyText || "{}");
    if (body.transaction_hash) return String(body.transaction_hash);
    if (body.data && body.data.transaction_hash) return String(body.data.transaction_hash);
    if (body.data && body.data.data && body.data.data.transaction_hash) return String(body.data.data.transaction_hash);
  } catch (e) {
    return "";
  }
  return "";
}

export default function () {
  if (!USER_ID) {
    return;
  }

  const internalHeaders = {
    "Content-Type": "application/json",
  };
  if (INTERNAL_DEPOSIT_TOKEN) {
    internalHeaders.Authorization = `Bearer ${INTERNAL_DEPOSIT_TOKEN}`;
  }

  const step1Res = http.post(
    `${INTERNAL_BASE}/api/v1/competitions/internal/deposit?skip_updating_bs=true`,
    JSON.stringify({
      user_id: USER_ID,
      amount: 10,
    }),
    { headers: internalHeaders }
  );

  const txHash = extractTransactionHash(step1Res.body);
  const step1Ok = check(step1Res, {
    "step1 status is 2xx": (r) => r.status >= 200 && r.status < 300,
    "step1 has transaction_hash": () => !!txHash,
  });

  if (!step1Ok || !txHash) {
    return;
  }

  http.post(
    `${INTERNAL_BASE}/api/v1/cashflow/internal`,
    JSON.stringify([
      {
        user_id: USER_ID,
        transaction_hash: txHash,
      },
    ]),
    {
      headers: {
        "Content-Type": "application/json",
      },
    }
  );
}

export function handleSummary(data) {
  const totalIterations = data.metrics.iterations ? data.metrics.iterations.values.count : 0;
  const httpFailures = data.metrics.http_req_failed ? data.metrics.http_req_failed.values.fails : 0;
  console.log(
    `=== DONE | Region: ${REGION} | Total iterations: ${totalIterations} | HTTP failures: ${httpFailures} ===`
  );
  return {};
}
