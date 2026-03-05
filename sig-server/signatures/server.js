const express = require("express");
const cors = require("cors");
const { ethers } = require("ethers");
const config = require("../config");

const app = express();
app.use(cors());
app.use(express.json());

// Optional: multi-user config for 5 users with different keys/order params
let multiUserConfig = null;
try {
  multiUserConfig = require("../multi-user-config");
  if (multiUserConfig && multiUserConfig.users && multiUserConfig.users.length) {
    multiUserConfig.wallets = multiUserConfig.users.map((u) =>
      u.PRIVATE_KEY && !u.PRIVATE_KEY.startsWith("REPLACE_")
        ? new ethers.Wallet(u.PRIVATE_KEY)
        : null
    );
  } else {
    multiUserConfig = null;
  }
} catch (e) {
  multiUserConfig = null;
}

// Health check + list endpoints (verify correct server is running)
app.get("/", (req, res) => {
  const endpoints = [
    "POST /sign-order",
    "POST /sign-order-multi (body: userIndex, salt, price, quantity, questionId, feeRateBps, intent, maker, priceInCents)",
    "POST /sign-create-proxy",
    "POST /sign-create-proxy-mm",
    "POST /sign-safe-approval",
    "GET /mm-info",
  ];
  res.json({
    ok: true,
    service: "pred-load-tests sig-server",
    endpoints,
    multi_user: !!multiUserConfig,
  });
});

// private key of MARKET MAKER (optional at startup; set config for signing)
const wallet = config.PRIVATE_KEY ? new ethers.Wallet(config.PRIVATE_KEY) : null;
const eoaAddress = config.EOA_ADDRESS || (wallet && wallet.address) || "";

// EIP-712 DOMAIN (fixed, correct)
// Normalize addresses to proper checksum format (convert to lowercase first if checksum is invalid)
const getChecksumAddress = (addr) => {
  try {
    return ethers.getAddress(addr);
  } catch (e) {
    // If checksum is invalid, convert to lowercase first then get checksum
    return ethers.getAddress(addr.toLowerCase());
  }
};

// EIP-712 domain: match backend reference (chainId as string "84532").
const domain = {
  name: config.DOMAIN_NAME,
  version: config.DOMAIN_VERSION,
  chainId: String(config.CHAIN_ID),
  verifyingContract: getChecksumAddress(config.VERIFYING_CONTRACT),
};

// SIGNATURE 3 — EIP-712 Order (place order). Field order and primary type MUST match backend.
// If backend verification fails (recovered address mismatch), get exact Order struct from backend:
// field order, types (uint256 vs uint8), and whether "timestamp" is included.
const orderFields = [
  { name: "salt", type: "uint256" },
  { name: "maker", type: "address" },
  { name: "signer", type: "address" },
  { name: "taker", type: "address" },
  { name: "price", type: "uint256" },
  { name: "quantity", type: "uint256" },
  { name: "expiration", type: "uint256" },
  { name: "nonce", type: "uint256" },
  { name: "questionId", type: "bytes32" },
  { name: "feeRateBps", type: "uint256" },
  { name: "intent", type: "uint8" },
  { name: "signatureType", type: "uint8" },
];
const types = { Order: orderFields };

// SIGNATURE 1: EIP-712 CreateProxy — for login-with-signature only
const createProxyDomain = {
  name: "Pred Contract Proxy Factory",
  chainId: 84532,
  verifyingContract: getChecksumAddress("0xBdb12f3B7C73590120164d0AEd7CcE31A3084040"),
};

// EIP-712 TYPES for CreateProxy
// Note: EIP712Domain is handled automatically by ethers.js
const createProxyTypes = {
  CreateProxy: [
    { name: "paymentToken", type: "address" },
    { name: "payment", type: "uint256" },
    { name: "paymentReceiver", type: "address" },
  ],
};

// CreateProxy message (static)
const createProxyMessage = {
  paymentToken: "0x0000000000000000000000000000000000000000",
  payment: 0,
  paymentReceiver: "0x0000000000000000000000000000000000000000",
};

// SIGNATURE 3: EIP-712 Order — for place-order API only. See docs/SIGNATURES.md
// If body.privateKey is provided, use it for this request (so Java can pass env PRIVATE_KEY and match login EOA).
app.post("/sign-order", async (req, res) => {
  const signerWallet = req.body.privateKey ? new ethers.Wallet(req.body.privateKey) : wallet;
  if (!signerWallet) return res.status(503).json({ error: "PRIVATE_KEY not configured: set in sig-server config or send privateKey in body" });
  try {
const { salt, price, quantity, questionId, feeRateBps, intent, maker: bodyMaker, signer: bodySigner, priceInCents } = req.body;
  const signerEoa = signerWallet.address;

    // Backend expects maker = proxy (trading contract), signer = EOA that signs (must match recovered address)
    const makerAddr = getChecksumAddress(bodyMaker || config.PROXY || signerEoa);
    const signerAddr = getChecksumAddress(bodySigner || signerEoa);

    // questionId must be bytes32: 0x + 64 hex chars. Normalize to lowercase for consistent encoding.
    const questionIdBytes32 = questionId && typeof questionId === "string"
      ? (questionId.startsWith("0x") ? questionId.toLowerCase() : "0x" + questionId.toLowerCase())
      : questionId;

    // 6 decimals. Backend usually expects price as human (e.g. 10 = $10) -> 10*1e6. Use priceInCents: true if API sends cents.
    const p = Number(price);
    const q = Number(quantity);
    const priceWei = String(priceInCents ? Math.round(p / 100 * 1e6) : Math.round(p * 1e6));
    const quantityWei = String(Math.round(q * 1e6));

    const message = {
      salt: String(salt),
      maker: makerAddr,
      signer: signerAddr,
      taker: "0x0000000000000000000000000000000000000000",
      price: priceWei/100,
      quantity: quantityWei,
      expiration: "0",
      nonce: "0",
      questionId: questionIdBytes32,
      feeRateBps: Number(feeRateBps ?? 0),
      intent: Number(intent ?? 0),
      signatureType: 2,
    };

    const signature = await signerWallet.signTypedData(domain, types, message);

    return res.json({
      ok: true,
      signature,
      eoa_address: signerEoa,
      maker: makerAddr,
      signer: signerAddr,
      signed_message: message,
    });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// Sign order for multi-user: body.userIndex selects which user's key to use (0..4)
app.post("/sign-order-multi", async (req, res) => {
  if (!multiUserConfig || !multiUserConfig.wallets) {
    return res.status(503).json({ error: "multi-user config not loaded; fill sig-server/multi-user-config.js" });
  }
  const userIndex = parseInt(req.body.userIndex, 10);
  if (isNaN(userIndex) || userIndex < 0 || userIndex >= multiUserConfig.users.length) {
    return res.status(400).json({ error: "userIndex must be 0.." + (multiUserConfig.users.length - 1) });
  }
  const user = multiUserConfig.users[userIndex];
  const walletMulti = multiUserConfig.wallets[userIndex];
  if (!walletMulti) {
    return res.status(503).json({ error: "user " + userIndex + " has no PRIVATE_KEY configured" });
  }
  try {
    const { salt, price, quantity, questionId, feeRateBps, intent, maker: bodyMaker, signer: bodySigner, priceInCents } = req.body;
    const makerAddr = getChecksumAddress(bodyMaker || user.PROXY || user.EOA_ADDRESS);
    const signerAddr = getChecksumAddress(bodySigner || user.EOA_ADDRESS);
    const qId = questionId || multiUserConfig.MARKET_ID;
    const questionIdBytes32 = qId && typeof qId === "string"
      ? (qId.startsWith("0x") ? qId.toLowerCase() : "0x" + qId.toLowerCase())
      : qId;
    const p = Number(price);
    const q = Number(quantity);
    const priceWei = String(priceInCents ? Math.round(p / 100 * 1e6) : Math.round(p * 1e6));
    const quantityWei = String(Math.round(q * 1e6));
    const message = {
      salt: String(salt),
      maker: makerAddr,
      signer: signerAddr,
      taker: "0x0000000000000000000000000000000000000000",
      price: priceWei,
      quantity: quantityWei,
      expiration: "0",
      nonce: "0",
      questionId: questionIdBytes32,
      feeRateBps: Number(feeRateBps ?? 0),
      intent: Number(intent ?? 0),
      signatureType: 2,
    };
    const signature = await walletMulti.signTypedData(domain, types, message);
    return res.json({
      ok: true,
      signature,
      eoa_address: user.EOA_ADDRESS,
      maker: makerAddr,
      signer: signerAddr,
      signed_message: message,
    });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// Get CreateProxy signature for market maker's EOA
app.post("/sign-create-proxy-mm", async (req, res) => {
  if (!wallet) return res.status(503).json({ error: "PRIVATE_KEY not configured in sig-server/config.js" });
  try {
    // Sign the CreateProxy message using market maker's wallet
    const signature = await wallet.signTypedData(
      createProxyDomain,
      createProxyTypes,
      createProxyMessage
    );

    return res.json({
      ok: true,
      wallet_address: wallet.address,
      signature,
    });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// New endpoint for CreateProxy EIP-712 signature (for login)
app.post("/sign-create-proxy", async (req, res) => {
  try {
    // Create a new wallet for each request (or use provided private key)
    const { privateKey } = req.body;
    const signerWallet = privateKey
      ? new ethers.Wallet(privateKey)
      : ethers.Wallet.createRandom();

    // Sign the CreateProxy message using EIP-712
    const signature = await signerWallet.signTypedData(
      createProxyDomain,
      createProxyTypes,
      createProxyMessage
    );

    return res.json({
      ok: true,
      wallet_address: signerWallet.address,
      signature: signature,
      private_key: signerWallet.privateKey, // Return private key for testing purposes
    });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// SIGNATURE 2: EIP-1193 — sign transactionHash for safe-approval/execute only. body: transactionHash, usePersonalSign (optional). See docs/SIGNATURES.md
app.post("/sign-safe-approval", async (req, res) => {
  const signerWallet = req.body.privateKey ? new ethers.Wallet(req.body.privateKey) : wallet;
  if (!signerWallet) return res.status(503).json({ error: "PRIVATE_KEY not configured in sig-server/config.js" });
  try {
    const { transactionHash, usePersonalSign } = req.body;
    if (!transactionHash) {
      return res.status(400).json({ error: "transactionHash is required" });
    }

    const digest = ethers.getBytes(transactionHash);
    const signature = usePersonalSign
      ? await signerWallet.signMessage(digest)
      : signerWallet.signingKey.sign(digest).serialized;

    return res.json({
      ok: true,
      signature,
      wallet_address: signerWallet.address,
    });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// Get market maker info (for k6: user_id + proxy from login when trading already enabled)
app.get("/mm-info", (req, res) => {
  return res.json({
    ok: true,
    eoa_address: eoaAddress,
    maker: config.PROXY || eoaAddress,
    signer: config.EOA_ADDRESS || eoaAddress,
    user_id: config.USER_ID,
    proxy: config.PROXY,
  });
});

const server = app.listen(5050, () => {
  console.log("Signature server running at http://localhost:5050/");
  console.log("Market Maker EOA:", eoaAddress);
  if (config.EOA_ADDRESS) {
    console.log("(Using provided EOA address)");
  } else {
    console.log("(Derived from private key:", wallet.address + ")");
  }
  console.log("Test: curl http://localhost:5050/");
});

server.on("error", (err) => {
  console.error("Server error:", err);
  process.exit(1);
});
