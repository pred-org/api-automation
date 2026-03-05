const config = require("../config");
const { loginWithConfig } = require("../signatures/login");

if (!config.PRIVATE_KEY) {
  console.error("Set PRIVATE_KEY in env or sig-server/config.js.");
  process.exit(1);
}

async function main() {
  const EOA_ADDRESS = config.EOA_ADDRESS || (config.PRIVATE_KEY ? new (require("ethers").Wallet)(config.PRIVATE_KEY).address : "");
  console.log("Wallet (EOA):", EOA_ADDRESS);
  if (config.API_KEY) console.log("Using existing API_KEY");
  console.log("");

  const { accessToken, userId, proxy, apiKeyUsed } = await loginWithConfig(config, { silent: false });

  console.log("Login successful.");
  console.log("Access Token:");
  console.log(accessToken);
  console.log("For k6:");
  console.log(`export TOKEN="${accessToken}"`);
  if (apiKeyUsed) console.log(`export API_KEY="${apiKeyUsed}"`);
  if (userId) console.log(`export USER_ID="${userId}"`);
  if (proxy) console.log(`export PROXY="${proxy}"`);
}

main().catch((e) => {
  console.error(e.message);
  process.exit(1);
});
