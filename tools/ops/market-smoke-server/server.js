/**
 * Market smoke server: POST /verify-markets — auth two users, discover markets by canonical name,
 * place matching long/short orders per market, verify positions.
 */
require("dotenv").config({ path: require("path").join(__dirname, ".env") });

const express = require("express");
const cors = require("cors");
const axios = require("axios");
const { v4: uuidv4 } = require("uuid");

const config = require("./config");

const app = express();
app.use(cors());
app.use(express.json({ limit: "1mb" }));

function normalizeHexKey(key) {
  if (!key || typeof key !== "string") return "";
  const t = key.trim();
  if (!t) return "";
  return t.startsWith("0x") ? t : `0x${t}`;
}

function makeSalt() {
  return BigInt(`0x${uuidv4().replace(/-/g, "")}`).toString();
}

function cookieHeader(refreshTokenRaw) {
  if (!refreshTokenRaw) return "";
  const v = refreshTokenRaw.startsWith("refresh_token=")
    ? refreshTokenRaw.slice("refresh_token=".length)
    : refreshTokenRaw;
  return `refresh_token=${v}`;
}

function extractRefreshTokenFromHeaders(setCookie) {
  if (!setCookie) return null;
  const parts = Array.isArray(setCookie) ? setCookie : [setCookie];
  for (const c of parts) {
    const m = /^refresh_token=([^;]+)/i.exec(c);
    if (m) return decodeURIComponent(m[1].trim());
  }
  return null;
}

function parseApiKey(data) {
  if (!data) return null;
  if (typeof data === "string" && data.trim()) return data.trim();
  const k =
    data?.data?.data?.api_key ||
    data?.data?.api_key ||
    data?.api_key;
  if (k && typeof k === "string" && k.trim()) return k.trim();
  return null;
}

function extractAccessToken(body) {
  if (!body || typeof body !== "object") return null;
  const paths = [
    body.access_token,
    body?.data?.access_token,
    body?.data?.data?.access_token,
    body?.data?.token,
    body?.token,
  ];
  for (const v of paths) {
    if (v && typeof v === "string" && v.trim()) return v.trim();
  }
  return null;
}

function extractUserId(body) {
  const v =
    body?.data?.user_id ||
    body?.data?.data?.user_id ||
    body?.user_id;
  return v && typeof v === "string" ? v.trim() : null;
}

function extractProxyWallet(body) {
  const v =
    body?.data?.proxy_wallet_address ||
    body?.data?.proxy_wallet_addr ||
    body?.data?.data?.proxy_wallet_address ||
    body?.data?.data?.proxy_wallet_addr ||
    body?.proxy_wallet_address ||
    body?.proxy_wallet_addr;
  return v && typeof v === "string" ? v.trim() : null;
}

function shortErr(e, maxLen = 500) {
  if (!e) return "unknown error";
  if (typeof e === "string") return e.length > maxLen ? e.slice(0, maxLen) + "..." : e;
  if (e.response) {
    const st = e.response.status;
    const data = e.response.data;
    let msg = "";
    if (typeof data === "string") msg = data;
    else if (data && typeof data === "object") msg = JSON.stringify(data);
    const out = `${st} - ${msg || e.message || "request failed"}`;
    return out.length > maxLen ? out.slice(0, maxLen) + "..." : out;
  }
  return e.message || String(e);
}

function createHttpClient(baseURL) {
  return axios.create({
    baseURL,
    timeout: config.HTTP_TIMEOUT_MS,
    validateStatus: () => true,
  });
}

async function checkSigServerReachable(sigUrl) {
  const client = createHttpClient(sigUrl);
  try {
    const res = await client.get("/", { timeout: 5000 });
    return res.status === 200 && res.data && res.data.ok === true;
  } catch (e) {
    return false;
  }
}

async function signCreateProxy(sigClient, privateKey) {
  const res = await sigClient.post("/sign-create-proxy", { privateKey });
  if (res.status !== 200 || !res.data?.ok) {
    throw new Error(`sign-create-proxy failed: ${res.status} ${JSON.stringify(res.data)}`);
  }
  return {
    wallet_address: res.data.wallet_address,
    signature: res.data.signature,
  };
}

async function createApiKey(internalClient) {
  const res = await internalClient.post("/api/v1/auth/internal/api-key/create", {});
  const key = parseApiKey(res.data);
  if (!key || (res.status !== 200 && res.status !== 201)) {
    throw new Error(`api-key create failed: ${res.status} body=${JSON.stringify(res.data)}`);
  }
  return key;
}

async function loginWithSignature(publicClient, apiKey, walletAddress, signature) {
  const now = Date.now();
  const tsSec = Math.floor(now / 1000);
  const body = {
    data: {
      wallet_address: walletAddress,
      signature,
      message: "Sign in to PRED Trading Platform",
      nonce: `nonce-${now}-${tsSec}`,
      chain_type: "base-sepolia",
      timestamp: tsSec,
    },
  };
  const res = await publicClient.post("/api/v1/auth/login-with-signature", body, {
    headers: {
      "Content-Type": "application/json",
      "X-API-Key": apiKey,
    },
  });
  if (res.status !== 200) {
    throw new Error(`login failed: ${res.status} ${JSON.stringify(res.data)}`);
  }
  const refreshRaw = extractRefreshTokenFromHeaders(res.headers["set-cookie"]);
  const accessToken = extractAccessToken(res.data);
  const userId = extractUserId(res.data);
  const proxyWallet = extractProxyWallet(res.data);
  if (!accessToken) throw new Error("login: no access_token in response");
  if (!refreshRaw) throw new Error("login: no refresh_token in Set-Cookie");
  return {
    accessToken,
    refreshCookie: refreshRaw,
    userId,
    proxyWallet,
  };
}

async function authenticateUser(sigClient, internalClient, publicClient, privateKeyHex) {
  const pk = normalizeHexKey(privateKeyHex);
  const { wallet_address, signature } = await signCreateProxy(sigClient, pk);
  const apiKey = await createApiKey(internalClient);
  const session = await loginWithSignature(publicClient, apiKey, wallet_address, signature);
  return {
    privateKey: pk,
    eoa: wallet_address,
    apiKey,
    accessToken: session.accessToken,
    refreshCookie: session.refreshCookie,
    userId: session.userId,
    proxyWallet: session.proxyWallet,
  };
}

function authHeaders(user) {
  return {
    Authorization: `Bearer ${user.accessToken}`,
    Cookie: cookieHeader(user.refreshCookie),
    "Content-Type": "application/json",
  };
}

async function discoverMarkets(publicClient, user, canonicalName) {
  const path = "/api/v1/market-discovery/discover";
  const res = await publicClient.get(path, {
    params: { cname: canonicalName, verbose: "true" },
    headers: authHeaders(user),
  });
  if (res.status !== 200) {
    throw new Error(`discover failed: ${res.status} ${JSON.stringify(res.data)}`);
  }
  const root = res.data?.data ?? res.data;
  const parentList = root?.parent_markets_list || [];
  let fixtureName = "";
  const markets = [];

  for (const entry of parentList) {
    const pmd = entry.parent_market_data || {};
    const fixture = entry.fixture || {};
    if (!fixtureName && fixture.name) fixtureName = fixture.name;
    if (!fixtureName && pmd.title) fixtureName = pmd.title;

    const mlist = entry.markets || [];
    for (const m of mlist) {
      if ((m.status || "").toLowerCase() !== "active") continue;
      markets.push({
        marketId: m.market_id,
        name: m.name || "",
        family: pmd.parent_market_family || "",
        marketLine: pmd.market_line != null ? String(pmd.market_line) : "0",
        parentMarketId: pmd.parent_market_id || "",
        title: pmd.title || "",
      });
    }
  }

  return { fixtureName: fixtureName || canonicalName, markets };
}

async function signOrder(sigClient, user, questionId, salt, timestampSec, side) {
  const isShort = typeof side === "string" && side.toLowerCase() === "short";
  const body = {
    salt,
    price: config.ORDER_PRICE,
    quantity: config.ORDER_QUANTITY,
    questionId,
    feeRateBps: 0,
    // Backend EIP-712 Order: LONG intent 0, SHORT intent 1 (OrderFlowTest / OrderTest).
    intent: isShort ? 1 : 0,
    signatureType: 2,
    maker: user.proxyWallet,
    signer: user.eoa,
    taker: "0x0000000000000000000000000000000000000000",
    expiration: "0",
    nonce: "0",
    priceInCents: false,
    timestamp: timestampSec,
    privateKey: user.privateKey,
  };
  const res = await sigClient.post("/sign-order", body);
  if (res.status !== 200 || !res.data?.ok) {
    throw new Error(`sign-order: ${res.status} ${JSON.stringify(res.data)}`);
  }
  return res.data.signature;
}

/**
 * PRED place-order amount (limit): cents-style price p in [0,100], qty in shares.
 * LONG: p * q / 100. SHORT: (100 - p) * q / 100. Matches docs/API_DOCUMENTATION.md and Java tests.
 */
function limitOrderAmount(priceStr, quantityStr, side) {
  const p = Number(priceStr);
  const q = Number(quantityStr);
  if (!Number.isFinite(p) || !Number.isFinite(q)) {
    throw new Error("invalid price or quantity for amount");
  }
  const isShort = typeof side === "string" && side.toLowerCase() === "short";
  const raw = isShort ? (100 - p) * (q / 100) : p * (q / 100);
  return raw.toFixed(2);
}

/** Path uses parent_market_id; body.market_id is the individual outcome market id. */
async function placeOrder(publicClient, user, parentMarketIdPath, marketIdBody, side, salt, timestampSec, signature) {
  const pathId = (parentMarketIdPath && String(parentMarketIdPath).trim())
    ? String(parentMarketIdPath).trim()
    : marketIdBody;
  const path = `/api/v1/order/${pathId}/place`;
  const price = config.ORDER_PRICE;
  const quantity = config.ORDER_QUANTITY;
  const amount = limitOrderAmount(price, quantity, side);
  const body = {
    salt,
    user_id: user.userId,
    market_id: marketIdBody,
    side,
    token_id: config.TOKEN_ID,
    price,
    quantity,
    amount,
    is_low_priority: false,
    signature,
    type: "limit",
    timestamp: timestampSec,
    reduce_only: false,
    fee_rate_bps: 0,
  };
  console.log("     FULL PLACE PAYLOAD:", JSON.stringify(body, null, 2));
  const res = await publicClient.post(path, body, {
    headers: {
      ...authHeaders(user),
      "X-Wallet-Address": user.eoa,
      "X-Proxy-Address": user.proxyWallet,
    },
  });
  return res;
}

/** Updates user.accessToken (and refresh cookie if Set-Cookie returned). */
async function tryRefreshAccessToken(publicClient, user) {
  try {
    const res = await publicClient.post(
      "/api/v1/auth/refresh/token",
      {},
      {
        headers: {
          "Content-Type": "application/json",
          Cookie: cookieHeader(user.refreshCookie),
        },
      }
    );
    if (res.status !== 200) return false;
    const token = extractAccessToken(res.data);
    if (!token) return false;
    user.accessToken = token;
    const newRef = extractRefreshTokenFromHeaders(res.headers["set-cookie"]);
    if (newRef) user.refreshCookie = newRef;
    return true;
  } catch {
    return false;
  }
}

async function placeOrderWith401Retry(
  publicClient,
  user,
  parentMarketIdPath,
  marketIdBody,
  side,
  salt,
  timestampSec,
  signature
) {
  let res = await placeOrder(
    publicClient,
    user,
    parentMarketIdPath,
    marketIdBody,
    side,
    salt,
    timestampSec,
    signature
  );
  if (res.status === 401 && (await tryRefreshAccessToken(publicClient, user))) {
    res = await placeOrder(
      publicClient,
      user,
      parentMarketIdPath,
      marketIdBody,
      side,
      salt,
      timestampSec,
      signature
    );
  }
  return res;
}

function normalizeMarketId(id) {
  if (!id || typeof id !== "string") return "";
  const s = id.trim().toLowerCase();
  return s.startsWith("0x") ? s : `0x${s}`;
}

function positionsContainMarket(body, marketId) {
  const want = normalizeMarketId(marketId);
  const list =
    body?.positions ||
    body?.data?.positions ||
    [];
  if (!Array.isArray(list)) return false;
  for (const p of list) {
    const mid = p?.market_id || p?.marketId;
    if (mid && normalizeMarketId(String(mid)) === want) return true;
  }
  return false;
}

async function verifyPosition(publicClient, user1, marketId) {
  const path = "/api/v1/portfolio/positions";
  const res = await publicClient.get(path, {
    params: { market_id: marketId },
    headers: authHeaders(user1),
  });
  if (res.status === 500) {
    return { ok: false, mode: "api_500" };
  }
  if (res.status !== 200) {
    return { ok: false, mode: "other", status: res.status };
  }
  return { ok: positionsContainMarket(res.data, marketId), mode: "ok" };
}

function placeStatusSummary(status) {
  if (status === 200 || status === 202) return "placed";
  return "failed";
}

app.post("/verify-markets", async (req, res) => {
  const started = Date.now();
  const { canonicalName, privateKey1, privateKey2 } = req.body || {};

  if (!canonicalName || typeof canonicalName !== "string" || !canonicalName.trim()) {
    return res.status(400).json({ error: "canonicalName is required" });
  }
  if (!privateKey1 || !privateKey2) {
    return res.status(400).json({ error: "privateKey1 and privateKey2 are required" });
  }

  const sigUrl = config.SIG_SERVER_URL.replace(/\/$/, "");
  const okSig = await checkSigServerReachable(sigUrl);
  if (!okSig) {
    return res.status(503).json({ error: `sig-server not reachable at ${sigUrl}` });
  }

  const sigClient = createHttpClient(sigUrl);
  const internalClient = createHttpClient(config.PRED_INTERNAL_URL.replace(/\/$/, ""));
  const publicClient = createHttpClient(config.PRED_BASE_URL.replace(/\/$/, ""));

  let user1;
  let user2;
  try {
    user1 = await authenticateUser(sigClient, internalClient, publicClient, privateKey1);
    console.log(`[AUTH] User 1 logged in: userId=${user1.userId} proxy=${user1.proxyWallet}`);
  } catch (e) {
    return res.status(401).json({ error: `Auth failed for user1: ${shortErr(e)}` });
  }
  try {
    user2 = await authenticateUser(sigClient, internalClient, publicClient, privateKey2);
    console.log(`[AUTH] User 2 logged in: userId=${user2.userId} proxy=${user2.proxyWallet}`);
  } catch (e) {
    return res.status(401).json({ error: `Auth failed for user2: ${shortErr(e)}` });
  }

  let discover;
  try {
    discover = await discoverMarkets(publicClient, user1, canonicalName.trim());
  } catch (e) {
    return res.status(500).json({ error: `Discover failed: ${shortErr(e)}` });
  }

  const { fixtureName, markets } = discover;
  if (!markets.length) {
    return res.status(404).json({ error: `No markets found for canonical name: ${canonicalName.trim()}` });
  }

  console.log(`[DISCOVER] Found ${markets.length} markets for ${canonicalName.trim()}`);

  const results = [];
  let passed = 0;
  let failed = 0;

  for (let i = 0; i < markets.length; i++) {
    const m = markets[i];
    const idx = i + 1;
    const marketStart = Date.now();
    const baseResult = {
      family: m.family,
      marketLine: m.marketLine,
      name: m.name,
      marketId: m.marketId,
      parentMarketId: m.parentMarketId || m.marketId,
    };

    const shortId = m.marketId && m.marketId.length > 12
      ? `${m.marketId.slice(0, 6)}...${m.marketId.slice(-4)}`
      : m.marketId;
    console.log(`[MARKET ${idx}/${markets.length}] ${m.family} | ${m.name} | ${shortId}`);

    let u1Order = { status: "pending", side: "long" };
    let u2Order = { status: "pending", side: "short" };
    let positionCreated = false;
    let failReason = null;

    try {
      const salt1 = makeSalt();
      const ts1 = Math.floor(Date.now() / 1000);
      let sig1;
      try {
        sig1 = await signOrder(sigClient, user1, m.marketId, salt1, ts1, "long");
        console.log("     User1 LONG signed OK");
      } catch (e) {
        const msg = shortErr(e);
        u1Order = { status: "failed", error: msg };
        u2Order = { status: "skipped", reason: "user1 order failed" };
        failReason = `user1 sign failed: ${msg}`;
        throw new Error(failReason);
      }

      let place1;
      try {
        place1 = await placeOrderWith401Retry(
          publicClient,
          user1,
          m.parentMarketId,
          m.marketId,
          "long",
          salt1,
          ts1,
          sig1
        );
      } catch (e) {
        const msg = shortErr(e);
        u1Order = { status: "failed", error: msg };
        u2Order = { status: "skipped", reason: "user1 order failed" };
        failReason = `user1 place failed: ${msg}`;
        throw new Error(failReason);
      }

      const st1 = place1.status;
      u1Order = {
        status: placeStatusSummary(st1),
        side: "long",
        ...(st1 !== 200 && st1 !== 202
          ? { error: `${st1} - ${typeof place1.data === "string" ? place1.data : JSON.stringify(place1.data)}` }
          : {}),
      };
      if (st1 !== 200 && st1 !== 202) {
        const detail =
          typeof place1.data === "string"
            ? place1.data.slice(0, 300)
            : JSON.stringify(place1.data).slice(0, 300);
        u2Order = { status: "skipped", reason: "user1 order failed" };
        failReason = `user1 order rejected: ${st1} - ${detail}`;
        throw new Error(failReason);
      }
      console.log("     User1 LONG placed OK");

      const salt2 = makeSalt();
      const ts2 = Math.floor(Date.now() / 1000);
      let sig2;
      try {
        sig2 = await signOrder(sigClient, user2, m.marketId, salt2, ts2, "short");
        console.log("     User2 SHORT signed OK");
      } catch (e) {
        const msg = shortErr(e);
        u2Order = { status: "failed", error: msg };
        failReason = `user2 sign failed: ${msg}`;
        throw new Error(failReason);
      }

      let place2;
      try {
        place2 = await placeOrderWith401Retry(
          publicClient,
          user2,
          m.parentMarketId,
          m.marketId,
          "short",
          salt2,
          ts2,
          sig2
        );
      } catch (e) {
        const msg = shortErr(e);
        u2Order = { status: "failed", error: msg };
        failReason = `user2 place failed: ${msg}`;
        throw new Error(failReason);
      }

      const st2 = place2.status;
      u2Order = {
        status: placeStatusSummary(st2),
        side: "short",
        ...(st2 !== 200 && st2 !== 202
          ? { error: `${st2} - ${typeof place2.data === "string" ? place2.data : JSON.stringify(place2.data)}` }
          : {}),
      };
      if (st2 !== 200 && st2 !== 202) {
        const detail =
          typeof place2.data === "string"
            ? place2.data.slice(0, 300)
            : JSON.stringify(place2.data).slice(0, 300);
        failReason = `user2 order rejected: ${st2} - ${detail}`;
        throw new Error(failReason);
      }
      console.log("     User2 SHORT placed OK");

      await new Promise((r) => setTimeout(r, config.POSITION_CHECK_DELAY_MS));

      const posCheck = await verifyPosition(publicClient, user1, m.marketId);
      if (posCheck.mode === "api_500") {
        positionCreated = "not_verified_positions_api_500";
        console.log("     Position check: positions API returned 500 (not verified)");
      } else if (posCheck.ok) {
        positionCreated = true;
        console.log("     Position verified OK");
      } else {
        positionCreated = false;
        console.log("     Position not found for market after delay");
      }

      const elapsed = Date.now() - marketStart;
      if (positionCreated === true || positionCreated === "not_verified_positions_api_500") {
        results.push({
          ...baseResult,
          status: positionCreated === true ? "success" : "warning",
          user1Order: u1Order,
          user2Order: u2Order,
          positionCreated,
          timeTakenMs: elapsed,
        });
        passed++;
      } else {
        failReason = "Orders accepted (202) but no position created — market may not be tradeable";
        results.push({
          ...baseResult,
          status: "failed",
          error: failReason,
          user1Order: u1Order,
          user2Order: u2Order,
          positionCreated: false,
          timeTakenMs: elapsed,
        });
        failed++;
        console.log(`     FAILED: ${failReason}`);
      }
    } catch (err) {
      const elapsed = Date.now() - marketStart;
      const msg = failReason || shortErr(err);
      results.push({
        ...baseResult,
        status: "failed",
        error: msg,
        user1Order: u1Order,
        user2Order: u2Order,
        positionCreated,
        timeTakenMs: elapsed,
      });
      failed++;
      console.log(`     FAILED: ${msg}`);
    }
  }

  const timeTakenMs = Date.now() - started;
  const total = markets.length;
  console.log(`[DONE] ${passed}/${total} passed, ${failed} failed (${(timeTakenMs / 1000).toFixed(1)}s)`);

  return res.json({
    canonicalName: canonicalName.trim(),
    fixtureName,
    totalMarkets: total,
    passed,
    failed,
    timeTakenMs,
    results,
  });
});

app.get("/health", (req, res) => {
  res.json({ ok: true, service: "market-smoke-server" });
});

const port = Number(config.PORT) || 5051;
app.listen(port, () => {
  console.log(`market-smoke-server listening on http://localhost:${port}`);
  console.log(`POST http://localhost:${port}/verify-markets`);
});
