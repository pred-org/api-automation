const { ethers } = require("ethers");
const https = require("https");

const config = require("../config");
const { loginWithConfig } = require("../signatures/login");
const API_KEY = process.env.API_KEY || config.API_KEY;
if (!API_KEY) {
  console.error("Set API_KEY in env or sig-server/config.js");
  process.exit(1);
}

/** Backend hex-decodes fields; numbers serialized with '.' cause "invalid byte: U+002E". Use string values only. */
function normalizePrepareData(obj) {
  const out = {};
  for (const [k, v] of Object.entries(obj)) {
    if (typeof v === "string") out[k] = v;
    else if (typeof v === "number") out[k] = Number.isInteger(v) ? String(v) : String(Math.floor(v));
    else if (typeof v === "bigint") out[k] = String(v);
    else out[k] = v;
  }
  return out;
}

async function enableTrading() {
  try {
    // Step 1: Get access token (uses login signature from signatures/login.js)
    console.log("Step 1: Getting access token...");
    const { accessToken, proxy: proxyWalletAddr } = await loginWithConfig(config, { silent: false });
    console.log("Access token received.");

    const proxyAddress = proxyWalletAddr || config.PROXY || config.EOA_ADDRESS;
    if (!proxyWalletAddr && !config.PROXY) {
      console.log("No proxy_wallet_addr in login response. Using EOA_ADDRESS. Add config.PROXY if prepare fails.");
    }

    // Step 2: Call prepare endpoint
    console.log("Step 2: Enabling trading (calling prepare endpoint)...");
    console.log(`   Proxy Wallet: ${proxyAddress}`);

    const preparePayload = {
      proxy_wallet_address: proxyAddress,
    };

    const prepareData = JSON.stringify(preparePayload);
    const prepareOptions = {
      hostname: "uat-frankfurt.pred.app",
      path: "/api/v1/user/safe-approval/prepare",
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "Authorization": `Bearer ${accessToken}`,
        "Content-Length": prepareData.length,
      },
    };

    const prepareResponse = await new Promise((resolve, reject) => {
      const req = https.request(prepareOptions, (res) => {
        let responseData = "";
        res.on("data", (chunk) => { responseData += chunk; });
        res.on("end", () => {
          try {
            const parsed = JSON.parse(responseData);
            console.log("Prepare Response:");
            console.log(JSON.stringify(parsed, null, 2));
            
            if (res.statusCode === 200 || res.statusCode === 201) {
              console.log("Prepare successful.");
              if (parsed.data?.data?.transactionHash) {
                console.log(`   Transaction Hash: ${parsed.data.data.transactionHash}`);
              }
              resolve(parsed);
            } else if (res.statusCode === 500 && responseData.includes("trading is already enabled")) {
              console.log("Trading is already enabled for this user.");
              console.log("Skip prepare/execute. For k6 use the same USER_ID and PROXY from login response.");
              resolve({ alreadyEnabled: true, proxyAddress });
            } else {
              console.error("Failed to prepare.");
              console.error(`   Status: ${res.statusCode}`);
              console.error(`   Response: ${responseData}`);
              reject(new Error(`Prepare failed: ${responseData}`));
            }
          } catch (e) {
            console.error("Failed to parse response:", responseData);
            reject(e);
          }
        });
      });

      req.on("error", (error) => {
        console.error("Request error:", error);
        reject(error);
      });

      req.write(prepareData);
      req.end();
    });

    if (prepareResponse.alreadyEnabled) {
      console.log("\n   Set in config or env: USER_ID and PROXY from login, then run k6 with TOKEN, API_KEY, USER_ID, PROXY.");
      return;
    }

    // Step 3: Sign the transaction hash and call execute
    const transactionData = prepareResponse.data?.data;
    if (!transactionData || !transactionData.transactionHash) {
      throw new Error("No transaction hash in prepare response");
    }

    console.log("Step 3: Signing transaction hash (raw hash for Safe)...");
    const txHash = transactionData.transactionHash;
    console.log(`   Transaction Hash: ${txHash}`);

    // Execute expects: wallet.signingKey.sign(hash).serialized (raw hash, no EIP-191). personal_sign causes GS026.
    const wallet = new ethers.Wallet(config.PRIVATE_KEY);
    const digest = ethers.getBytes(txHash);
    const signature = process.env.USE_PERSONAL_SIGN === "1"
      ? await wallet.signMessage(digest)
      : wallet.signingKey.sign(digest).serialized;
    console.log(`   Mode: ${process.env.USE_PERSONAL_SIGN === "1" ? "EIP-1193 personal_sign" : "raw hash (Safe-compatible)"}`);
    console.log(`   Signature: ${signature}`);
    console.log(`   Signature length: ${signature.length} chars`);

    // Step 4: Call execute endpoint
    console.log("Step 4: Executing transaction (calling execute endpoint)...");
    console.log(`   Safe Address (Proxy): ${transactionData.safeAddress}`);
    console.log(`   Signing with EOA: ${wallet.address}`);
    console.log(`   Transaction Hash: ${txHash}`);

    // Send prepare data as-is (same types as prepare response) to avoid "Invalid request body"
    const executePayload = {
      data: transactionData,
      signature: signature,
    };
    
    console.log(`   Payload keys: ${Object.keys(executePayload).join(", ")}`);
    console.log(`   Data keys: ${Object.keys(transactionData).join(", ")}`);

    const executeData = JSON.stringify(executePayload);
    const executeOptions = {
      hostname: "uat-frankfurt.pred.app",
      path: "/api/v1/user/safe-approval/execute",
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "Authorization": `Bearer ${accessToken}`,
        "Content-Length": executeData.length,
      },
    };

    return new Promise((resolve, reject) => {
      const req = https.request(executeOptions, (res) => {
        let responseData = "";
        res.on("data", (chunk) => { responseData += chunk; });
        res.on("end", () => {
          try {
            const parsed = JSON.parse(responseData);
            console.log("Execute Response:");
            console.log(JSON.stringify(parsed, null, 2));
            
            if (res.statusCode === 200 || res.statusCode === 201) {
              console.log("Trading enabled successfully.");
              resolve(parsed);
            } else {
              console.error("Failed to execute.");
              console.error(`   Status: ${res.statusCode}`);
              console.error(`   Response: ${responseData}`);
              
              // Check if it's a transaction failure
              if (responseData.includes("transaction failed")) {
                const txMatch = responseData.match(/transaction failed: (0x[a-fA-F0-9]+)/);
                if (txMatch) {
                  console.error(`\n   Transaction Hash: ${txMatch[1]}`);
                  console.error(`   Check transaction on Base Sepolia explorer:`);
                  console.error(`   https://sepolia.basescan.org/tx/${txMatch[1]}`);
                }
                console.error(`\n   Note: This is an on-chain transaction failure.`);
                console.error(`   Possible reasons:`);
                console.error(`   - Transaction already executed`);
                console.error(`   - Insufficient gas or funds`);
                console.error(`   - Safe wallet requires additional signatures`);
                console.error(`   - Contract state issue`);
              }
              
              reject(new Error(`Execute failed: ${responseData}`));
            }
          } catch (e) {
            console.error("Failed to parse response:", responseData);
            reject(e);
          }
        });
      });

      req.on("error", (error) => {
        console.error("Request error:", error);
        reject(error);
      });

      req.write(executeData);
      req.end();
    });
  } catch (error) {
    console.error("Error:", error.message);
    throw error;
  }
}

// Run
enableTrading()
  .then(() => {
    console.log("Done. Trading should now be enabled.");
    process.exit(0);
  })
  .catch((error) => {
    console.error("Failed:", error.message);
    process.exit(1);
  });
