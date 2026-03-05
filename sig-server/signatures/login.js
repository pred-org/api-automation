/**
 * SIGNATURE 1 — EIP-712 (login-with-signature)
 *
 * Signs CreateProxy typed data with the user's private key; wallet_address (EOA)
 * is sent with the signature to prove ownership. Used only for login.
 * Not used for execute or place order.
 *
 * API: POST /api/v1/auth/login-with-signature
 * See: docs/SIGNATURES.md
 */
const { ethers } = require("ethers");
const https = require("https");

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

/**
 * @param {object} config - { PRIVATE_KEY, EOA_ADDRESS, API_KEY? }
 * @param {{ silent?: boolean }} opts - silent: true = no console output
 * @returns {Promise<{ accessToken: string, userId?: string, proxy?: string }>}
 */
async function loginWithConfig(config, opts = {}) {
  const silent = opts.silent === true;
  const PRIVATE_KEY = config.PRIVATE_KEY;
  const EOA_ADDRESS = config.EOA_ADDRESS || (PRIVATE_KEY ? new ethers.Wallet(PRIVATE_KEY).address : "");
  let API_KEY = config.API_KEY;

  if (!PRIVATE_KEY) {
    throw new Error("config must have PRIVATE_KEY");
  }
  if (!API_KEY || (typeof API_KEY === "string" && !API_KEY.trim())) {
    const { createApiKey } = require("../api/create-api-key-client");
    if (!silent) console.log("API_KEY not set; creating new API key...");
    API_KEY = await createApiKey();
    if (!silent) console.log("API key created, logging in...");
  }

  const wallet = new ethers.Wallet(PRIVATE_KEY);
  const signature = await wallet.signTypedData(domain, types, message);
  const timestamp = Math.floor(Date.now() / 1000);
  const nonce = `nonce-${Date.now()}-${timestamp}`;

  const loginPayload = {
    data: {
      wallet_address: EOA_ADDRESS,
      signature,
      message: "Sign in to PRED Trading Platform",
      nonce,
      chain_type: "base-sepolia",
      timestamp,
    },
  };

  const body = JSON.stringify(loginPayload);
  const options = {
    hostname: "uat-frankfurt.pred.app",
    path: "/api/v1/auth/login-with-signature",
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "X-API-Key": API_KEY,
      "Content-Length": Buffer.byteLength(body),
    },
  };

  return new Promise((resolve, reject) => {
    const req = https.request(options, (res) => {
      let data = "";
      res.on("data", (chunk) => (data += chunk));
      res.on("end", () => {
        try {
          const parsed = JSON.parse(data);
          if (res.statusCode !== 200 && res.statusCode !== 201) {
            reject(new Error(`Login failed: ${res.statusCode} ${data}`));
            return;
          }
          const d = parsed.data || parsed;
          // Response structure: data.access_token, data.data.user_id, data.data.proxy_wallet_addr
          const accessToken = d.access_token || parsed.access_token || d.token || parsed.token;
          const userId = d.data?.user_id || d.user_id || parsed.user_id;
          const proxy = d.data?.proxy_wallet_addr || d.proxy_wallet_addr || d.proxy_wallet_address || parsed.proxy_wallet_addr;
          if (!accessToken) reject(new Error("No access_token in login response"));
          else resolve({ accessToken, userId, proxy, apiKeyUsed: API_KEY });
        } catch (e) {
          reject(e);
        }
      });
    });
    req.on("error", reject);
    req.write(body);
    req.end();
  });
}

module.exports = { loginWithConfig };
