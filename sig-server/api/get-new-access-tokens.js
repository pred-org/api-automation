#!/usr/bin/env node
/**
 * Get new access tokens for all users (login-with-signature).
 * Uses multi-user-config.js: each user's PRIVATE_KEY + API_KEY to sign and login.
 * Writes sig-server/users-tokens.json for k6. Prints tokens and export lines.
 *
 * Usage: node get-new-access-tokens.js
 */
const fs = require("fs");
const path = require("path");
const { loginWithConfig } = require("../signatures/login");
const multiUserConfig = require("../multi-user-config");

const OUT_FILE = path.join(__dirname, "..", "users-tokens.json");

function isPlaceholder(val) {
  return !val || (typeof val === "string" && (val.startsWith("REPLACE_WITH_") || val.startsWith("REPLACE_AFTER_") || val.startsWith("REPLACE_")));
}

async function main() {
  const users = multiUserConfig.users;
  if (!users || users.length === 0) {
    console.error("No users in multi-user-config.js");
    process.exit(1);
  }

  const result = {
    MARKET_ID: multiUserConfig.MARKET_ID,
    MARKET_ID_PATH: multiUserConfig.MARKET_ID_PATH || null,
    TOKEN_ID: multiUserConfig.TOKEN_ID,
    users: [],
  };

  console.log("\nGetting new access tokens (login-with-signature) for all users...\n");

  for (let i = 0; i < users.length; i++) {
    const u = users[i];
    if (isPlaceholder(u.PRIVATE_KEY) || isPlaceholder(u.API_KEY)) {
      console.error(`[X] ${u.id}: PRIVATE_KEY or API_KEY not set in multi-user-config.js`);
      result.users.push({ id: u.id, userIndex: i, error: "PRIVATE_KEY or API_KEY not set", order: u.order });
      continue;
    }

    const config = {
      PRIVATE_KEY: u.PRIVATE_KEY,
      EOA_ADDRESS: u.EOA_ADDRESS,
      API_KEY: u.API_KEY,
    };

    try {
      const { accessToken, userId, proxy } = await loginWithConfig(config, { silent: true });
      const proxyAddr = proxy || u.PROXY;
      const uid = userId || u.USER_ID;
      result.users.push({
        id: u.id,
        userIndex: i,
        accessToken,
        proxy: proxyAddr,
        eoa: u.EOA_ADDRESS,
        userId: uid,
        order: u.order,
      });

      console.log("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
      console.log(` ${u.id} (index ${i})`);
      console.log("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
      console.log("Proxy:    ", proxyAddr || "(none)");
      console.log("EOA:      ", u.EOA_ADDRESS);
      console.log("User ID:  ", uid || "(none)");
      console.log("");
      console.log("Access Token:");
      console.log(accessToken);
      console.log("");
      console.log("Export (copy for terminal):");
      console.log(`export USER${i + 1}_TOKEN="${accessToken}"`);
      if (proxyAddr) console.log(`export USER${i + 1}_PROXY="${proxyAddr}"`);
      if (uid) console.log(`export USER${i + 1}_USER_ID="${uid}"`);
      console.log("");
    } catch (e) {
      console.error(`[X] ${u.id}: ${e.message}`);
      result.users.push({ id: u.id, userIndex: i, error: e.message, order: u.order });
    }
  }

  fs.writeFileSync(OUT_FILE, JSON.stringify(result, null, 2), "utf8");
  console.log("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
  console.log("Wrote " + OUT_FILE);
  console.log("   From project root:");
  console.log("   Stress (3 users, 1000 random): ./place-order/run-k6-place-order-stress-3users.sh");
  console.log("   Multi-user (matching):        ./place-order/run-k6-place-order-multi.sh");
  console.log("   (Start sig-server first: cd sig-server && npm start)\n");
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
