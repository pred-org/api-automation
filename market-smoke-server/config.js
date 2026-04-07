require("dotenv").config({ path: require("path").join(__dirname, ".env") });

module.exports = {
  PRED_BASE_URL: process.env.PRED_BASE_URL || "https://uat-frankfurt.pred.app",
  PRED_INTERNAL_URL: process.env.PRED_INTERNAL_URL || "http://api-internal.uat-frankfurt.pred.app",
  SIG_SERVER_URL: process.env.SIG_SERVER_URL || "http://localhost:5050",
  TOKEN_ID: "0x1234567890abcdef1234567890abcdef12345678",
  ORDER_PRICE: "30",
  ORDER_QUANTITY: "100",
  PORT: process.env.PORT || 5051,
  POSITION_CHECK_DELAY_MS: Number(process.env.POSITION_CHECK_DELAY_MS) || 2000,
  HTTP_TIMEOUT_MS: Number(process.env.HTTP_TIMEOUT_MS) || 120000,
};
