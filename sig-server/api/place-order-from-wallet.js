#!/usr/bin/env node
/**
 * Place 1 order using only PRIVATE_KEY + EOA_ADDRESS.
 * This script: (1) creates an API key via internal API, (2) enables trading, (3) places one order.
 *
 * Usage:
 *   Set PRIVATE_KEY and EOA_ADDRESS in sig-server/config.js (or env), then:
 *   cd sig-server && node api/place-order-from-wallet.js
 *
 * Prerequisites:
 *   - Sig-server must be running (npm start in another terminal) for the order signature.
 *   - Internal API must be reachable for create API key.
 */
const http = require("http");
const path = require("path");
const { spawnSync } = require("child_process");
const config = require("../config");

const INTERNAL_CREATE_API_KEY_URL = "http://api-internal.uat-frankfurt.pred.app/api/v1/auth/internal/api-key/create";

function createApiKey() {
  return new Promise((resolve, reject) => {
    const options = {
      hostname: "api-internal.uat-frankfurt.pred.app",
      path: "/api/v1/auth/internal/api-key/create",
      method: "POST",
      headers: { "Content-Type": "application/json", "Content-Length": 2 },
    };
    const req = http.request(options, (res) => {
      let data = "";
      res.on("data", (chunk) => (data += chunk));
      res.on("end", () => {
        if (res.statusCode !== 200 && res.statusCode !== 201) {
          reject(new Error(`Create API key failed: ${res.statusCode} ${data}`));
          return;
        }
        try {
          const parsed = typeof data === "string" && data.trim() ? JSON.parse(data) : {};
          let raw = parsed.data?.api_key ?? parsed.api_key ?? parsed.data;
          if (raw && typeof raw === "object" && raw.api_key) raw = raw.api_key;
          const apiKey = typeof raw === "string" ? raw.trim() : "";
          if (apiKey) resolve(apiKey);
          else reject(new Error("No api_key in response: " + data));
        } catch (e) {
          reject(e);
        }
      });
    });
    req.on("error", reject);
    req.write("{}");
    req.end();
  });
}

async function main() {
  if (!config.PRIVATE_KEY || !config.EOA_ADDRESS) {
    console.error("Set PRIVATE_KEY and EOA_ADDRESS in sig-server/config.js (or env).");
    process.exit(1);
  }

  console.log("Step 1: Creating API key (internal API)…");
  let apiKey;
  try {
    apiKey = await createApiKey();
    if (typeof apiKey !== "string" || !apiKey) {
      throw new Error("API key from server is not a string");
    }
    apiKey = apiKey.trim();
    console.log("[OK] API key created (length " + apiKey.length + ").");
  } catch (e) {
    console.error("[X]", e.message);
    console.error("   Ensure internal API is reachable (VPN/network).");
    process.exit(1);
  }

  const env = { ...process.env, API_KEY: String(apiKey) };
  const sigServerDir = path.join(__dirname, "..");

  console.log("\nStep 2: Enabling trading (prepare → sign → execute)…");
  const enable = spawnSync("node", ["execution/enable-trading.js"], { cwd: sigServerDir, env, stdio: "inherit" });
  if (enable.status !== 0) {
    console.error("\n[X] Enable trading failed. Fix errors above, then run again.");
    console.error("\n   If you see 'invalid API key': the key from the internal API may not be");
    console.error("   accepted by the public login endpoint. Try using an API key from the PRED");
    console.error("   dashboard: set it in config as API_KEY, then run:");
    console.error("     node execution/enable-trading.js");
    console.error("     node api/place-order.js");
    process.exit(1);
  }
  console.log("[OK] Trading enabled (or already enabled).");

  console.log("\nStep 3: Placing one order…");
  const place = spawnSync("node", ["api/place-order.js"], { cwd: sigServerDir, env, stdio: "inherit" });
  if (place.status !== 0) {
    console.error("\n[X] Place order failed. Check output above.");
    process.exit(1);
  }

  console.log("\n[OK] Done. One order flow completed.");
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
