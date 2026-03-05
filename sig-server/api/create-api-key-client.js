/**
 * Create API key via internal API. No auth required (POST {}).
 * Used by login when API_KEY is not set.
 */
const http = require("http");

const INTERNAL_HOST = process.env.API_BASE_URI_INTERNAL_HOST || "api-internal.uat-frankfurt.pred.app";
const CREATE_PATH = "/api/v1/auth/internal/api-key/create";

/**
 * @returns {Promise<string>} API key string
 */
function createApiKey() {
  return new Promise((resolve, reject) => {
    const options = {
      hostname: INTERNAL_HOST,
      path: CREATE_PATH,
      method: "POST",
      headers: { "Content-Type": "application/json", "Content-Length": 2 },
    };
    const req = http.request(options, (res) => {
      let data = "";
      res.on("data", (chunk) => (data += chunk));
      res.on("end", () => {
        if (res.statusCode !== 200 && res.statusCode !== 201) {
          reject(new Error(`Create API key failed: ${res.statusCode} ${data}`));
          return;
        }
        try {
          const parsed = data && data.trim() ? JSON.parse(data) : {};
          const apiKey = parsed.data?.data?.api_key || parsed.data?.api_key || parsed.api_key || (typeof parsed.data === "string" ? parsed.data : null);
          if (apiKey) resolve(apiKey);
          else reject(new Error("No api_key in response: " + data));
        } catch (e) {
          reject(e);
        }
      });
    });
    req.on("error", reject);
    req.write("{}");
    req.end();
  });
}

module.exports = { createApiKey };
