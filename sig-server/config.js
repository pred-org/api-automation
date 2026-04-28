/**
 * You provide PRIVATE_KEY and (optional) EOA_ADDRESS. API_KEY is created automatically if not set.
 * PROXY and USER_ID come from login response.
 * Env from project-root .env (loaded by signatures/server.js) or process.env; config defaults below.
 *
 * Java tests: They send privateKey in the request body to POST /sign-create-proxy and POST /sign-order.
 * The sig-server derives the wallet address from that key and does NOT use EOA_ADDRESS for those requests.
 * So changing EOA_ADDRESS here does not affect the tests; the tests use the address derived from
 * testdata.properties private.key. EOA_ADDRESS is used only when no privateKey is in the body (e.g. GET /mm-info).
 */
module.exports = {
  // --- You provide these (env overrides) ---
  PRIVATE_KEY: process.env.PRIVATE_KEY || "",
  EOA_ADDRESS: process.env.EOA_ADDRESS || "",

  // --- Optional: set to skip auto-create; otherwise created on first login ---
  API_KEY: process.env.API_KEY || "",

  // --- From login response (do not set; framework gets them) ---
  USER_ID: process.env.USER_ID || null,
  PROXY: process.env.PROXY || null,

  // --- Optional: for place-order ---
  MARKET_ID: process.env.MARKET_ID || null,
  TOKEN_ID: process.env.TOKEN_ID || null,

  // --- EIP-712 domain (UAT: fixed) ---
  CHAIN_ID: 84532,
  VERIFYING_CONTRACT: "0x398e870065121Ee3b0565e2b925cBD3f25df2Ce1",
  DOMAIN_NAME: "Pred CTF Exchange",
  DOMAIN_VERSION: "1",
};
