/**
 * SIGNATURE 3 — EIP-712 Order (place order)
 *
 * Verifies / recovers signer from the signature used in place-order. This is the
 * same EIP-712 Order typed data signed by POST /sign-order (salt, maker, signer,
 * taker, price, quantity, ...). Not used for login or execute.
 *
 * Usage:
 *   node verify-order-hash.js '<signature_hex>'                    — use message from config (maker/signer/questionId)
 *   node verify-order-hash.js '<signature_hex>' '<message_json>'  — use exact message (e.g. from sign-order response)
 * Get the exact message from the sig server response (signed_message) when you call POST /sign-order.
 * See: docs/SIGNATURES.md
 */

const { ethers } = require("ethers");
const config = require("../config");

const domain = {
  chainId: config.CHAIN_ID,
  name: config.DOMAIN_NAME,
  verifyingContract: config.VERIFYING_CONTRACT,
  version: config.DOMAIN_VERSION,
};

// Order field order must match frontend: salt, maker, signer, taker, price, quantity, expiration, nonce, questionId, feeRateBps, intent, signatureType
const types = {
  Order: [
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
  ],
};

const getChecksumAddress = (addr) => {
  if (!addr) return "0x0000000000000000000000000000000000000000";
  try {
    return ethers.getAddress(addr);
  } catch (e) {
    return ethers.getAddress(addr.toLowerCase());
  }
};

// Default example: current config (maker = proxy, signer = EOA, questionId = market id). Price/quantity in 6 decimals like sign-order.
const wallet = config.PRIVATE_KEY ? new ethers.Wallet(config.PRIVATE_KEY) : null;
const signerAddr = getChecksumAddress(wallet ? wallet.address : config.EOA_ADDRESS);
const makerAddr = getChecksumAddress(config.PROXY || config.EOA_ADDRESS || signerAddr);
const marketId = config.MARKET_ID || "0xfaa8c7e1fd82aa80aae5c8859c2bb54e01e69badd720605dff89494dd974b400";
const examplePrice = process.env.PLACE_ORDER_PRICE || "30";
const exampleQty = process.env.PLACE_ORDER_QTY || "200";
const priceWei = String(Math.round((Number(examplePrice) / 100) * 1e6));
const quantityWei = String(Math.round(Number(exampleQty) * 1e6));

const exampleMessage = {
  salt: String(process.env.SALT || Math.floor(Math.random() * 1e16)),
  maker: makerAddr,
  signer: signerAddr,
  taker: "0x0000000000000000000000000000000000000000",
  price: priceWei,
  quantity: quantityWei,
  expiration: "0",
  nonce: "0",
  questionId: marketId,
  feeRateBps: 0,
  intent: 0,
  signatureType: 2,
};

const sigHex = process.argv[2];
const messageArg = process.argv[3];
let message;
try {
  message = messageArg ? JSON.parse(messageArg) : exampleMessage;
} catch (e) {
  console.error("Invalid message JSON:", e.message);
  process.exit(1);
}

const digest = ethers.TypedDataEncoder.hash(domain, types, message);
console.log("EIP-712 digest (what gets signed):", digest);
console.log("Message used:", JSON.stringify(message, null, 2));

if (sigHex) {
  const recovered = ethers.verifyTypedData(domain, types, message, sigHex);
  console.log("Recovered address from signature:", recovered);
  console.log("Expected signer:               ", message.signer);
  console.log("Match:", recovered.toLowerCase() === message.signer.toLowerCase());
} else {
  console.log("\nPass a signature to verify: node verify-order-hash.js '<signature_hex>' ['<message_json>']");
}
