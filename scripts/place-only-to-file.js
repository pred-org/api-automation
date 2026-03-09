#!/usr/bin/env node
/**
 * Place orders only and write order IDs to a JSON file. Use with cancel_only when you need
 * more than ~400 cancels (k6 setup payload limit).
 *
 * Prereqs: sig-server running, real session env. Run:  source .env.session
 *
 * Usage:
 *   source .env.session
 *   node scripts/place-only-to-file.js
 *   # Optional: PLACE_SEC=120 OUT_FILE=./order-ids.json node scripts/place-only-to-file.js
 *
 * Then run cancel_only with the file (use the absolute path printed below; k6 resolves paths relative to the script):
 *   K6_ORDER_IDS_FILE=<absolute-path> K6_MODE=cancel_only k6 run k6/place-cancel-rate-limit.js
 */

const fs = require("fs");
const path = require("path");

const BASE_URL = process.env.BASE_URL || "https://uat-frankfurt.pred.app";
const SIG_SERVER_URL = process.env.SIG_SERVER_URL || "http://localhost:5050";
const MARKET_ID = process.env.MARKET_ID || "0xf83d64fbb43a9b199109a96fee6291fc66b9fe0a5cd38b0bd2901fd10d7f1900";
const TOKEN_ID = process.env.TOKEN_ID || "0x1234567890abcdef1234567890abcdef12345678";
const PRICE = "30";
const QTY = "100";

const accessToken = process.env.ACCESS_TOKEN;
const refreshCookie = process.env.REFRESH_COOKIE || "";
const eoa = process.env.EOA;
const proxy = process.env.PROXY;
const userId = process.env.USER_ID;

const placeSec = Math.max(1, parseInt(process.env.PLACE_SEC || "120", 10));
const outFile = path.resolve(process.cwd(), process.env.OUT_FILE || "order-ids.json");

function sleep(ms) {
  return new Promise((r) => setTimeout(r, ms));
}

async function main() {
  if (!accessToken || !eoa || !proxy || !userId) {
    console.error("Set ACCESS_TOKEN, EOA, PROXY, USER_ID (e.g. source .env.session)");
    process.exit(1);
  }

  const placeHeaders = {
    "Content-Type": "application/json",
    "Authorization": "Bearer " + accessToken,
    "X-Wallet-Address": eoa,
    "X-Proxy-Address": proxy,
  };
  if (refreshCookie) placeHeaders["Cookie"] = refreshCookie;

  const orderIds = [];
  const placeEnd = Date.now() + placeSec * 1000;
  let loggedSignFailure = false;
  let loggedPlaceFailure = false;

  console.log("Placing orders for " + placeSec + "s, writing IDs to " + outFile + " ...");

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

    let signRes;
    try {
      signRes = await fetch(SIG_SERVER_URL + "/sign-order", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: signBody,
      });
    } catch (e) {
      console.error("sign-order request failed:", e.message);
      await sleep(500);
      continue;
    }

    if (signRes.status !== 200) {
      if (!loggedSignFailure) {
        loggedSignFailure = true;
        const body = await signRes.text();
        console.error("sign-order failed: status=" + signRes.status + " body=" + (body.slice(0, 200) || "(none)"));
      }
      continue;
    }
    let sig;
    try {
      sig = (await signRes.json()).signature;
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

    let placeRes;
    try {
      placeRes = await fetch(BASE_URL + "/api/v1/order/" + MARKET_ID + "/place", {
        method: "POST",
        headers: placeHeaders,
        body: placeBody,
      });
    } catch (e) {
      console.error("place request failed:", e.message);
      await sleep(500);
      continue;
    }

    if (placeRes.status >= 200 && placeRes.status < 300) {
      try {
        const oid = (await placeRes.json()).order_id;
        if (oid) orderIds.push(oid);
      } catch (e) {}
    } else {
      if (!loggedPlaceFailure) {
        loggedPlaceFailure = true;
        const body = await placeRes.text();
        console.error("place failed: status=" + placeRes.status + " body=" + (body.slice(0, 200) || "(none)"));
      }
    }
    await sleep(50);
  }

  fs.writeFileSync(outFile, JSON.stringify(orderIds));
  console.log("Wrote " + orderIds.length + " order IDs to " + outFile);
  if (orderIds.length > 0) {
    console.log("Run cancel_only with: K6_ORDER_IDS_FILE=" + outFile + " K6_MODE=cancel_only k6 run k6/place-cancel-rate-limit.js");
  }
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
