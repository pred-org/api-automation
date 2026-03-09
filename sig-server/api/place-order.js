// sig-server/api/place-order.js — API call: login + sign order + place order

const http = require("http");
const https = require("https");
const config = require("../config");
const { loginWithConfig } = require("../signatures/login");

// LOCAL SIG SERVER ENDPOINTS
const SIGN_ORDER_URL = "http://localhost:5050/sign-order";
const SIGN_CREATE_PROXY_URL = "http://localhost:5050/sign-create-proxy-mm";

// MARKET + ORDER DETAILS — match working curl: hex market id in URL and body, same body shape (no user_id).
const MARKET_ID = process.env.MARKET_ID || config.MARKET_ID || "0xf83d64fbb43a9b199109a96fee6291fc66b9fe0a5cd38b0bd2901fd10d7f1900";
const TOKEN_ID = process.env.TOKEN_ID || config.TOKEN_ID || "0x1234567890abcdef1234567890abcdef12345678";
const PRICE = process.env.PLACE_ORDER_PRICE || "30";
const QTY = process.env.PLACE_ORDER_QTY || "100";

function httpPost(url, payload) {
  return new Promise((resolve, reject) => {
    const parsed = new URL(url);
    const data = JSON.stringify(payload);
    const isHttps = parsed.protocol === "https:";
    const options = {
      hostname: parsed.hostname,
      port: parsed.port || (isHttps ? 443 : 80),
      path: parsed.pathname,
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "Content-Length": Buffer.byteLength(data),
      },
    };
    const client = isHttps ? https : http;
    const req = client.request(options, (res) => {
      let body = "";
      res.on("data", (chunk) => (body += chunk));
      res.on("end", () => resolve({ status: res.statusCode, body }));
    });
    req.on("error", reject);
    req.write(data);
    req.end();
  });
}

async function main() {
  console.log("→ Logging in…");

  const { accessToken, userId, proxy } = await loginWithConfig(config, { silent: true });

  console.log("[OK] Logged in");
  console.log("USER_ID:", userId);
  console.log("PROXY:", proxy);

  // 1) SIGN ORDER
  const salt = String(Math.floor(Math.random() * 1e16));

  console.log("\n→ Requesting order signature…");

  const sigRes = await httpPost(SIGN_ORDER_URL, {
    salt,
    price: PRICE,
    quantity: QTY,
    questionId: MARKET_ID,
    feeRateBps: 0,
    intent: 0,
    maker: proxy,
  });

  if (sigRes.status !== 200) {
    console.error("SIGN ORDER FAILED:", sigRes.body);
    process.exit(1);
  }

  const sigData = JSON.parse(sigRes.body);
  const signature = sigData.signature;
  const eoaAddress = sigData.eoa_address;
  if (sigData.signed_message) {
    console.log("[OK] Order signature OK (signed_message available for verify-order-hash.js)");
  } else {
    console.log("[OK] Order signature OK");
  }

  // 2) SIGN CREATE PROXY
  console.log("\n→ Requesting createProxySignature…");

  const cpxRes = await httpPost(SIGN_CREATE_PROXY_URL, {});

  let createProxySignature = null;
  if (cpxRes.status === 200) {
    createProxySignature = JSON.parse(cpxRes.body).signature;
    console.log("[OK] CreateProxy signature OK");
  } else {
    console.log("[WARN] CreateProxy signature failed — will try order without it");
  }

  // 3) BUILD ORDER PAYLOAD — structure from working curl (no user_id in body)
  const timestamp = Math.floor(Date.now() / 1000);
  const amount = String(Math.floor((Number(PRICE) * Number(QTY)) / 100)); // price in cents: 30 * 100 → 30
  const payload = {
    salt,
    market_id: MARKET_ID,
    side: "long",
    token_id: TOKEN_ID,
    price: PRICE,
    quantity: QTY,
    amount,
    is_low_priority: false,
    signature,
    type: "limit",
    timestamp,
    expiration: "0",
    reduce_only: false,
    fee_rate_bps: 0,
  };

  // 4) SEND ORDER — URL and headers match working curl
  const orderUrl = `https://uat-frankfurt.pred.app/api/v1/order/${MARKET_ID}/place`;
  console.log("\n→ Placing order…");

  const res = await new Promise((resolve, reject) => {
    const data = JSON.stringify(payload);
    const url = new URL(orderUrl);

    const options = {
      hostname: url.hostname,
      path: url.pathname + url.search,
      method: "POST",
      headers: {
        "Accept": "*/*",
        "Content-Type": "application/json",
        "Authorization": `Bearer ${accessToken}`,
        "X-Wallet-Address": eoaAddress,
        "X-Proxy-Address": proxy,
      },
    };
    if (config.API_KEY) options.headers["X-API-Key"] = config.API_KEY;
    options.headers["Content-Length"] = Buffer.byteLength(data);

    const req = https.request(options, (res) => {
      let body = "";
      res.on("data", (chunk) => (body += chunk));
      res.on("end", () => resolve({ status: res.statusCode, body }));
    });

    req.on("error", reject);
    req.write(data);
    req.end();
  });

  console.log("\n=== ORDER RESPONSE ===");
  console.log("STATUS:", res.status);
  console.log("BODY:", res.body);

  if (res.status === 202) {
    console.log("\nORDER PLACED SUCCESSFULLY!");
  } else if (res.status === 400 && res.body && res.body.includes("recovered address") && res.body.includes("does not match expected signer")) {
    const m = res.body.match(/recovered address (0x[a-fA-F0-9]{40}) does not match expected signer (0x[a-fA-F0-9]{40})/);
    if (m) {
      console.log("\n[WARN] Signature mismatch (wrong wallet):");
      console.log("   Your PRIVATE_KEY signs as (recovered):", m[1]);
      console.log("   Backend expects the proxy owner to sign:", m[2]);
      console.log("   Fix: In sig-server/config.js set PRIVATE_KEY and EOA_ADDRESS to the wallet that owns this proxy (expected signer above).");
    } else {
      console.log("\n[WARN] Order failed — signature validation failed. Use the PRIVATE_KEY for the wallet that owns the proxy.");
    }
  } else {
    console.log("\n[WARN] Order failed — check output above.");
  }
}

main();
