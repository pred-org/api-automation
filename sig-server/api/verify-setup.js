const { ethers } = require("ethers");
const config = require("../config");

console.log("Verifying sig-server config");

let derivedAddress = "(n/a)";
let wallet = null;
if (config.PRIVATE_KEY) {
  wallet = new ethers.Wallet(config.PRIVATE_KEY);
  derivedAddress = wallet.address;
} else {
  console.warn(
    "PRIVATE_KEY is empty — fine for load tests using POST /wallets + signing_id; scripts that need a default MM wallet should set PRIVATE_KEY."
  );
}

console.log("Config:");
console.log("   PRIVATE_KEY: " + (config.PRIVATE_KEY ? "(set)" : "(not set)"));
console.log("   EOA (derived): " + derivedAddress);
console.log("   EOA_ADDRESS: " + (config.EOA_ADDRESS || "(use derived)"));
console.log("   API_KEY: " + (config.API_KEY ? config.API_KEY.substring(0, 20) + "..." : "(not set)"));
console.log("   USER_ID: " + (config.USER_ID || "(from login)"));
console.log("   PROXY: " + (config.PROXY || "(from login)"));
console.log("USER_ID and PROXY come from login response; run login flow to fill them.");
console.log("Run: node get-access-token.js and use the printed export lines.");
