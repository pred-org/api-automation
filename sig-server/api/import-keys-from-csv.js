/**
 * Load keys from the Go keygen CSV into the in-process wallet registry.
 *
 * CSV format (header row):
 *   address,private_key
 *   or: address,private_key,mnemonic
 *
 * Usage:
 *   node api/import-keys-from-csv.js keys.csv [http://localhost:5050]
 *
 * Prints JSON lines: signing_id,address (for correlating with your load-test users).
 */
require("dotenv").config({ path: require("path").join(__dirname, "../../.env") });

const fs = require("fs");
const http = require("http");
const https = require("https");

const csvPath = process.argv[2];
const baseUrl = process.argv[3] || process.env.SIG_SERVER_URL || "http://localhost:5050";

if (!csvPath) {
  console.error("Usage: node import-keys-from-csv.js <keys.csv> [sig_server_url]");
  process.exit(1);
}

function parseCsvPrivateKeys(text) {
  const lines = text.split(/\r?\n/).filter((l) => l.trim().length);
  if (lines.length < 2) return [];
  const header = lines[0].split(",").map((h) => h.trim().toLowerCase());
  const pkIdx = header.findIndex((h) => h === "private_key" || h === "privatekey");
  if (pkIdx < 0) {
    throw new Error('CSV must include a "private_key" column');
  }
  const keys = [];
  for (let i = 1; i < lines.length; i++) {
    const row = lines[i].split(",");
    if (row.length <= pkIdx) continue;
    const pk = row[pkIdx] && row[pkIdx].trim();
    if (pk) keys.push(pk);
  }
  return keys;
}

function postJson(urlStr, body) {
  const u = new URL(urlStr.replace(/\/$/, "") + "/wallets/batch");
  const lib = u.protocol === "https:" ? https : http;
  const payload = Buffer.from(JSON.stringify(body), "utf8");
  return new Promise((resolve, reject) => {
    const req = lib.request(
      {
        hostname: u.hostname,
        port: u.port || (u.protocol === "https:" ? 443 : 80),
        path: u.pathname,
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "Content-Length": payload.length,
        },
      },
      (res) => {
        let data = "";
        res.on("data", (c) => (data += c));
        res.on("end", () => {
          try {
            resolve({ status: res.statusCode, data: JSON.parse(data) });
          } catch (e) {
            resolve({ status: res.statusCode, raw: data });
          }
        });
      }
    );
    req.on("error", reject);
    req.write(payload);
    req.end();
  });
}

(async () => {
  const text = fs.readFileSync(csvPath, "utf8");
  const private_keys = parseCsvPrivateKeys(text);
  if (!private_keys.length) {
    console.error("No private keys found in CSV.");
    process.exit(1);
  }
  const result = await postJson(baseUrl, { private_keys });
  if (result.status !== 200 || !result.data || !result.data.ok) {
    console.error("Import failed:", result.status, result.raw || result.data);
    process.exit(1);
  }
  console.log(JSON.stringify(result.data, null, 2));
})();
