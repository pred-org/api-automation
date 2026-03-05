const { ethers } = require("ethers");
const config = require("../config");

console.log("Verifying sig-server config");

if (!config.PRIVATE_KEY) {
  console.error("PRIVATE_KEY is empty. Set it in sig-server/config.js or env PRIVATE_KEY.");
  process.exit(1);
}

const wallet = new ethers.Wallet(config.PRIVATE_KEY);
const derivedAddress = wallet.address;

console.log("Config:");
console.log("   PRIVATE_KEY: " + config.PRIVATE_KEY.substring(0, 10) + "..." + config.PRIVATE_KEY.slice(-6));
console.log("   EOA (derived): " + derivedAddress);
console.log("   EOA_ADDRESS: " + (config.EOA_ADDRESS || "(use derived)"));
console.log("   API_KEY: " + (config.API_KEY ? config.API_KEY.substring(0, 20) + "..." : "(not set)"));
console.log("   USER_ID: " + (config.USER_ID || "(from login)"));
console.log("   PROXY: " + (config.PROXY || "(from login)"));
console.log("USER_ID and PROXY come from login response; run login flow to fill them.");
console.log("Run: node get-access-token.js and use the printed export lines.");
