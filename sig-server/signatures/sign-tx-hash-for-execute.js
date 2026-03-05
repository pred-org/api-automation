#!/usr/bin/env node
/**
 * SIGNATURE 2 — EIP-1193 (execute API)
 *
 * Signs the transactionHash from safe-approval/prepare. This signature is used
 * only for POST /api/v1/user/safe-approval/execute. Same EOA (Safe owner) as
 * login; different from login (EIP-712) and from place-order (EIP-712 Order).
 *
 * Two variants produced (backend may accept one or the other):
 *   - Raw: signingKey.sign(hash).serialized (Safe-compatible)
 *   - EIP-1193 personal_sign: signMessage(hash)
 *
 * Usage:
 *   node sign-tx-hash-for-execute.js <transactionHash> [userIndex]
 *
 * userIndex: 0 = config.js (user1), 1..4 = multi-user-config users.
 * See: docs/SIGNATURES.md
 */
const { ethers } = require("ethers");
const config = require("../config");

const txHash = process.argv[2];
const userIndex = process.argv[3] !== undefined ? parseInt(process.argv[3], 10) : 0;

if (!txHash || !txHash.startsWith("0x")) {
  console.error("Usage: node sign-tx-hash-for-execute.js <transactionHash> [userIndex]");
  process.exit(1);
}

function getWallet(index) {
  if (index === 0) {
    return { wallet: new ethers.Wallet(config.PRIVATE_KEY), label: "config.js (user1)" };
  }
  const multiUser = require("../multi-user-config");
  const u = multiUser.users[index];
  if (!u || !u.PRIVATE_KEY) {
    throw new Error("userIndex " + index + " not configured in multi-user-config.js");
  }
  return { wallet: new ethers.Wallet(u.PRIVATE_KEY), label: u.id };
}

async function main() {
  const { wallet, label } = getWallet(userIndex);
  const digest = ethers.getBytes(txHash);

  // 1) Raw hash (Safe-compatible) - default used by enable-trading.js
  const rawSignature = wallet.signingKey.sign(digest).serialized;

  // 2) EIP-1193 personal_sign (sign message = hash bytes)
  const personalSignSignature = await wallet.signMessage(ethers.getBytes(txHash));

  console.log("\nTransaction Hash:", txHash);
  console.log("Signer:", label, wallet.address);
  console.log("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
  console.log("  USE THIS ONE (Raw - Safe-compatible):");
  console.log("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
  console.log(rawSignature);
  console.log("\n  WARNING: Using wrong signature causes transaction failure.");
  console.log("   Multiple failures may block the account for 24 hours.");
  console.log("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
  console.log(" (Backup - EIP-1193 personal_sign - only if raw fails):");
  console.log("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
  console.log(personalSignSignature);
  console.log("\nPostman execute body: { \"data\": { <prepare response data> }, \"signature\": \"<paste RAW above>\" }\n");
}

main().catch((e) => {
  console.error(e.message);
  process.exit(1);
});
