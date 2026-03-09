#!/usr/bin/env node
/**
 * Print order signature for place-order (body field "signature").
 * Sig-server must be running: node signatures/server.js
 * Uses config.js (PRIVATE_KEY, MARKET_ID, etc.) or env overrides.
 *
 * Usage: node scripts/print-order-signature.js
 */
const http = require("http");
const config = require("../config");

const SIGN_ORDER_URL = "http://localhost:5050/sign-order";
const MARKET_ID = process.env.MARKET_ID || config.MARKET_ID || "0xf83d64fbb43a9b199109a96fee6291fc66b9fe0a5cd38b0bd2901fd10d7f1900";
const TOKEN_ID = process.env.TOKEN_ID || config.TOKEN_ID || "0x1234567890abcdef1234567890abcdef12345678";
const PRICE = process.env.PLACE_ORDER_PRICE || "30";
const QTY = process.env.PLACE_ORDER_QTY || "100";

function httpPost(url, payload) {
  return new Promise((resolve, reject) => {
    const parsed = new URL(url);
    const data = JSON.stringify(payload);
    const options = {
      hostname: parsed.hostname,
      port: parsed.port || 80,
      path: parsed.pathname,
      method: "POST",
      headers: { "Content-Type": "application/json", "Content-Length": Buffer.byteLength(data) },
    };
    const req = http.request(options, (res) => {
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
  const salt = String(Math.floor(Math.random() * 1e16));
  const payload = {
    salt,
    price: PRICE,
    quantity: QTY,
    questionId: MARKET_ID,
    feeRateBps: "0",
    intent: 0,
    maker: config.PROXY || undefined,
  };
  if (config.PRIVATE_KEY) payload.privateKey = config.PRIVATE_KEY;

  const res = await httpPost(SIGN_ORDER_URL, payload);
  if (res.status !== 200) {
    console.error("sign-order failed:", res.body);
    process.exit(1);
  }

  const data = JSON.parse(res.body);
  const timestamp = Math.floor(Date.now() / 1000);
  const amount = String(Math.floor(Number(PRICE) * Number(QTY)));

  console.log("SIGNATURE_FOR_PLACE_ORDER:", data.signature);
  console.log("salt:", salt);
  console.log("timestamp:", timestamp);
  console.log("market_id:", MARKET_ID);
  console.log("token_id:", TOKEN_ID);
  console.log("price:", PRICE, "quantity:", QTY, "amount:", amount);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
