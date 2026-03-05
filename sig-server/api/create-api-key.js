/**
 * Create API key via internal API.
 * No auth required: POST with empty body {}.
 * Optional: pass a Bearer token if your environment requires it.
 *
 * Usage:
 *   node create-api-key.js
 *   node create-api-key.js "optional_bearer_token"
 */
const http = require("http");

const token = process.argv[2];

console.log("Creating new API key...");
console.log(`   URL: http://api-internal.uat-frankfurt.pred.app/api/v1/auth/internal/api-key/create`);
if (token) {
  console.log(`   Using Bearer token: ${token.substring(0, 20)}...`);
}
console.log("");

const headers = {
  "Content-Type": "application/json",
  "Content-Length": 2,
};
if (token) {
  headers.Authorization = `Bearer ${token}`;
}

const options = {
  hostname: "api-internal.uat-frankfurt.pred.app",
  path: "/api/v1/auth/internal/api-key/create",
  method: "POST",
  headers,
};

const req = http.request(options, (res) => {
  let data = "";
  res.on("data", (chunk) => (data += chunk));
  res.on("end", () => {
    if (res.statusCode === 200 || res.statusCode === 201) {
      try {
        const parsed = typeof data === "string" && data.trim() ? JSON.parse(data) : {};
        const apiKey = parsed.data?.api_key || parsed.api_key || parsed.data;
        if (apiKey) {
          console.log("\nAPI Key created successfully!\n");
          console.log("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
          console.log(apiKey);
          console.log("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
          console.log("Next steps:");
          console.log("   1. Copy the API key above");
          console.log("   2. Update sig-server/config.js:");
          console.log("      API_KEY: process.env.API_KEY || \"<paste-api-key-here>\",");
          console.log("   3. Or set it as env var:");
          console.log(`      export API_KEY="${apiKey}"`);
          console.log("   4. Then run: node get-access-token.js\n");
          process.exit(0);
        }
      } catch (e) {
        // fall through to generic error
      }
    }

    console.error("\nFailed to create API key");
    console.error(`   Status: ${res.statusCode}`);
    console.error(`   Response: ${data || "(empty)"}\n`);
    if (res.statusCode === 401) {
      console.error("Tip: You may need to provide a Bearer token:");
      console.error("   node create-api-key.js \"YOUR_BEARER_TOKEN\"\n");
    } else if (res.statusCode === 404) {
      console.error("Tip: Endpoint not found (404). Possible causes:");
      console.error("   - Internal API URL or path may have changed — confirm with DevOps");
      console.error("   - You may need to be on VPN / internal network to reach this host");
      console.error("   - Try creating API key via PRED dashboard or Postman if you have the correct URL\n");
    }
    process.exit(1);
  });
});

req.on("error", (e) => {
  console.error("\nRequest error:", e.message);
  if (e.code === "ENOTFOUND" || e.code === "ECONNREFUSED") {
    console.error("\nTip: Make sure you're on the correct network/VPN to reach the internal API\n");
  }
  process.exit(1);
});
req.write("{}");
req.end();
