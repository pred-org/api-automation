/**
 * Multi-user config: 1 user (fresh start). Add more users later for stress tests.
 *
 * Run: node api/get-new-access-tokens.js to fetch ACCESS_TOKEN and get USER_ID/PROXY from login.
 */
const MARKET_ID = process.env.MARKET_ID || "0xfaa8c7e1fd82aa80aae5c8859c2bb54e01e69badd720605dff89494dd974b400";
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
      PRIVATE_KEY: process.env.USER1_PRIVATE_KEY || "0x2d07b07b6d6e09d4e718cb7c29ed60f7a5c5ef2c92ae55fd3e34f9c4151186f5",
      EOA_ADDRESS: process.env.USER1_EOA || "0x2D5a425b242eeA189DDa1740D92fA0D0Bb27b925",
      PROXY: process.env.USER1_PROXY || null,
      API_KEY: process.env.USER1_API_KEY || "931d2521-7d33-45e3-8cfb-322b7e7496ae-6c039587-0091-41ba-8ca0-53ad8ccee17f",
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
