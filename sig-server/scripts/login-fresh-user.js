#!/usr/bin/env node
/**
 * One-off login for a fresh user using EIP-712 CreateProxy signature.
 * Uses env: PRIVATE_KEY, EOA_ADDRESS (optional, derived from key), API_KEY.
 *
 * Usage:
 *   PRIVATE_KEY=... API_KEY=... [EOA_ADDRESS=0x...] node scripts/login-fresh-user.js
 */
const { loginWithConfig } = require("../signatures/login");

const PRIVATE_KEY = process.env.PRIVATE_KEY;
const EOA_ADDRESS = process.env.EOA_ADDRESS || "";
const API_KEY = process.env.API_KEY;

if (!PRIVATE_KEY || !API_KEY) {
  console.error("Set PRIVATE_KEY and API_KEY in env.");
  process.exit(1);
}

const config = {
  PRIVATE_KEY: PRIVATE_KEY.trim(),
  EOA_ADDRESS: EOA_ADDRESS.trim() || undefined,
  API_KEY: API_KEY.trim(),
};

async function main() {
  console.log("Generating EIP-712 CreateProxy signature and logging in...\n");
  
  const { accessToken, userId, proxy } = await loginWithConfig(config, { silent: false });
  console.log("\n--- Login successful ---");
  
  console.log("Access Token:", accessToken ? accessToken.slice(0, 30) + "..." : "(none)");
  if (userId) console.log("USER_ID:", userId);
  if (proxy) console.log("PROXY:", proxy);
  console.log("\nFor k6 / config:");
  console.log(`export TOKEN="${accessToken}"`);
  console.log(`export API_KEY="${API_KEY}"`);
  if (userId) console.log(`export USER_ID="${userId}"`);
  if (proxy) console.log(`export PROXY="${proxy}"`);
}

main().catch((e) => {
  console.error(e.message);
  process.exit(1);
});
