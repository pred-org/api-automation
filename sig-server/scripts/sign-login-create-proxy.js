#!/usr/bin/env node
/**
 * Generate EIP-712 CreateProxy signature for login-with-signature API.
 * Outputs the signature and full request body (no API call).
 *
 * Usage:
 *   PRIVATE_KEY=0x... EOA_ADDRESS=0x... node scripts/sign-login-create-proxy.js
 *   PRIVATE_KEY=0x... node scripts/sign-login-create-proxy.js   # EOA derived from key
 */
const { ethers } = require("ethers");

const domain = {
  name: "Pred Contract Proxy Factory",
  chainId: 84532,
  verifyingContract: "0xBdb12f3B7C73590120164d0AEd7CcE31A3084040",
};

const types = {
  CreateProxy: [
    { name: "paymentToken", type: "address" },
    { name: "payment", type: "uint256" },
    { name: "paymentReceiver", type: "address" },
  ],
};

const message = {
  paymentToken: "0x0000000000000000000000000000000000000000",
  payment: 0,
  paymentReceiver: "0x0000000000000000000000000000000000000000",
};

function normalizeKey(key) {
  if (!key) return key;
  const s = String(key).trim();
  return s.startsWith("0x") ? s : "0x" + s;
}

async function main() {
  let privateKey = process.env.PRIVATE_KEY;
  let eoaAddress = process.env.EOA_ADDRESS;

  if (!privateKey) {
    console.error("Set PRIVATE_KEY (with or without 0x prefix).");
    process.exit(1);
  }

  privateKey = normalizeKey(privateKey);
  const wallet = new ethers.Wallet(privateKey);
  const derivedAddress = wallet.address;

  if (!eoaAddress) {
    eoaAddress = derivedAddress;
  } else {
    eoaAddress = eoaAddress.trim();
    if (eoaAddress.toLowerCase() !== derivedAddress.toLowerCase()) {
      console.warn("Warning: EOA_ADDRESS does not match private key. Using EOA_ADDRESS for wallet_address in body.");
    }
  }

  const signature = await wallet.signTypedData(domain, types, message);
  const timestamp = Math.floor(Date.now() / 1000);
  const nonce = `nonce-${Date.now()}-${timestamp}`;

  const loginBody = {
    data: {
      wallet_address: eoaAddress,
      signature,
      message: "Sign in to PRED Trading Platform",
      nonce,
      chain_type: "base-sepolia",
      timestamp,
    },
  };

  console.log("EIP-712 CreateProxy signature (for login-with-signature):\n");
  console.log(signature);
  console.log("\n--- Full request body (JSON) ---\n");
  console.log(JSON.stringify(loginBody, null, 2));
  console.log("\n--- curl example ---");
  console.log(
    "curl -sS -X POST 'https://uat-frankfurt.pred.app/api/v1/auth/login-with-signature' \\"
  );
  console.log("  -H 'Content-Type: application/json' \\");
  console.log("  -H 'X-API-Key: YOUR_API_KEY' \\");
  console.log("  -d '" + JSON.stringify(loginBody) + "'");
}

main().catch((e) => {
  console.error(e.message);
  process.exit(1);
});
