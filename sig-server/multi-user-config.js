/**
 * Multi-user config: 1 user (fresh start). Add more users later for stress tests.
 *
 * Run: node api/get-new-access-tokens.js to fetch ACCESS_TOKEN and get USER_ID/PROXY from login.
 */
const MARKET_ID = process.env.MARKET_ID || "0xf83d64fbb43a9b199109a96fee6291fc66b9fe0a5cd38b0bd2901fd10d7f1900";
const MARKET_ID_PATH = process.env.MARKET_ID_PATH || null;
const TOKEN_ID = process.env.TOKEN_ID || "0x1234567890abcdef1234567890abcdef12345678";

function amountCents(priceCents, quantity) {
  return String(Math.floor((Number(priceCents) * Number(quantity)) / 100));
}

module.exports = {
  MARKET_ID,
  MARKET_ID_PATH,
  TOKEN_ID,

  users: [
    {
      id: "user1",
      PRIVATE_KEY: process.env.USER1_PRIVATE_KEY || "",
      EOA_ADDRESS: process.env.USER1_EOA || "",
      PROXY: process.env.USER1_PROXY || null,
      API_KEY: process.env.USER1_API_KEY || "",
      USER_ID: process.env.USER1_USER_ID || null,
      ACCESS_TOKEN: process.env.USER1_TOKEN || null,
      order: {
        intent: 0,
        side: "long",
        price: "30",
        quantity: "100",
        amount: amountCents(30, 100),
        token_id: TOKEN_ID,
      },
    },
  ],
};
