package com.pred.apitests.service;

import com.pred.apitests.config.Config;
import com.pred.apitests.util.SecondUserContext;
import com.pred.apitests.util.SessionFileWriter;
import com.pred.apitests.util.TokenManager;
import com.pred.apitests.util.UserSession;
import com.pred.apitests.base.BaseService;
import com.pred.apitests.model.request.LoginRequest;
import com.pred.apitests.model.response.LoginResponse;
import com.pred.apitests.model.response.SignCreateProxyResponse;
import io.restassured.response.Response;

import java.util.HashMap;
import java.util.Map;

/**
 * Auth API: login (with signature), create API key. Refresh is cookie-based: capture Set-Cookie: refresh_token from login response;
 * send Cookie: refresh_token=... on every authenticated request; when token is near expiry (~40 min), call login again with that cookie.
 */
public class AuthService extends BaseService {

    private static final String LOGIN_PATH = "/api/v1/auth/login-with-signature";
    private static final String CREATE_API_KEY_PATH = "/api/v1/auth/internal/api-key/create";
    private static final String REFRESH_TOKEN_PATH = "/api/v1/auth/refresh/token";
    private static final String COOKIE_HEADER = "Cookie";
    private static final String REFRESH_TOKEN_COOKIE_NAME = "refresh_token";

    /**
     * Create API key (internal). Returns raw response; body may be JSON (e.g. data.data.api_key) or plain key string.
     */
    public Response createApiKey() {
        String base = getInternalBaseUri();
        return post(base, CREATE_API_KEY_PATH, "{}");
    }

    /**
     * Parse API key from createApiKey response. Tries data.data.api_key, then data.api_key, then body as string.
     */
    public String parseApiKey(Response response) {
        if (response == null || response.getStatusCode() != 200) return null;
        try {
            String fromPath = response.jsonPath().getString("data.data.api_key");
            if (fromPath != null && !fromPath.isBlank()) return fromPath.trim();
            fromPath = response.jsonPath().getString("data.api_key");
            if (fromPath != null && !fromPath.isBlank()) return fromPath.trim();
        } catch (Exception ignored) { }
        String body = response.getBody().asString();
        return (body != null && !body.isBlank()) ? body.trim() : null;
    }

    /**
     * POST /auth/refresh/token with Cookie: refresh_token=... Returns new access_token in body (e.g. data.access_token or access_token).
     */
    public Response refreshToken(String refreshCookieValue) {
        if (refreshCookieValue == null || refreshCookieValue.isBlank()) return null;
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put(COOKIE_HEADER, refreshCookieValue);
        String base = getPublicBaseUri();
        return post(base, REFRESH_TOKEN_PATH, "{}", headers);
    }

    /**
     * Login with signature. Requires API key and body. Optionally pass refreshCookie (e.g. "refresh_token=...") for re-login refresh.
     */
    public Response login(String apiKey, LoginRequest body) {
        return login(apiKey, body, null);
    }

    /**
     * Login with signature and optional Cookie: refresh_token=... (for refresh: re-call login with stored cookie to get new access_token).
     */
    public Response login(String apiKey, LoginRequest body, String refreshCookieValue) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("X-API-Key", apiKey);
        if (refreshCookieValue != null && !refreshCookieValue.isBlank()) {
            headers.put(COOKIE_HEADER, refreshCookieValue);
        }
        String base = getPublicBaseUri();
        return post(base, LOGIN_PATH, body, headers);
    }

    /**
     * Extract refresh_token from login response. Response may have Set-Cookie: refresh_token=... or cookie in response.
     */
    public String extractRefreshTokenFromResponse(Response response) {
        String cookie = response.getCookie(REFRESH_TOKEN_COOKIE_NAME);
        if (cookie != null && !cookie.isBlank()) return cookie;
        String setCookie = response.getHeader("Set-Cookie");
        if (setCookie != null && setCookie.startsWith(REFRESH_TOKEN_COOKIE_NAME + "=")) {
            String value = setCookie.substring((REFRESH_TOKEN_COOKIE_NAME + "=").length());
            int semicolon = value.indexOf(';');
            return semicolon > 0 ? value.substring(0, semicolon).trim() : value.trim();
        }
        return null;
    }

    /**
     * Parse response body as LoginResponse. Returns null if parse fails.
     */
    public LoginResponse parseLoginResponse(Response response) {
        try {
            return response.as(LoginResponse.class);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Try common JSON paths for access token when POJO mapping misses it (e.g. different key or nesting).
     */
    public String extractAccessTokenFromResponse(Response response) {
        try {
            var json = response.jsonPath();
            String[] paths = { "access_token", "data.access_token", "data.data.access_token", "data.token", "token" };
            for (String path : paths) {
                String v = json.getString(path);
                if (v != null && !v.isBlank()) return v.trim();
            }
        } catch (Exception ignored) { }
        return null;
    }

    private static String getStringPath(io.restassured.path.json.JsonPath json, String... paths) {
        for (String path : paths) {
            try {
                String v = json.getString(path);
                if (v != null && !v.isBlank()) return v.trim();
            } catch (Exception ignored) { }
        }
        return null;
    }

    /**
     * Fill userId and proxyWalletAddress on parsed from JsonPath when POJO left them null.
     */
    public void fillLoginResponseFromJsonPath(Response response, LoginResponse parsed) {
        if (parsed.getUserId() != null && parsed.getProxyWalletAddress() != null) return;
        try {
            var json = response.jsonPath();
            String userId = getStringPath(json, "data.user_id", "data.data.user_id", "user_id");
            String proxy = getStringPath(json, "data.proxy_wallet_address", "data.proxy_wallet_addr",
                    "data.data.proxy_wallet_address", "data.data.proxy_wallet_addr", "proxy_wallet_address", "proxy_wallet_addr");
            if (userId != null || proxy != null) {
                LoginResponse.LoginResponseData data = parsed.getData();
                if (data == null) {
                    data = new LoginResponse.LoginResponseData();
                    parsed.setData(data);
                }
                if (userId != null) data.setUserId(userId);
                if (proxy != null) data.setProxyWalletAddress(proxy);
            }
        } catch (Exception ignored) { }
    }

    /**
     * Login and update TokenManager on 200. Captures Set-Cookie: refresh_token and stores it; sets tokenSetAtMs.
     */
    public LoginResponse loginAndStore(String apiKey, LoginRequest body) {
        Response response = login(apiKey, body);
        return loginAndStoreFromResponse(response);
    }

    /**
     * Store access_token, refresh_token (from response cookie), user_id, proxy from a successful login response.
     */
    public LoginResponse loginAndStoreFromResponse(Response response) {
        if (response.getStatusCode() != 200) return null;
        LoginResponse parsed = parseLoginResponse(response);
        if (parsed == null) return null;
        String token = parsed.getAccessToken();
        if (token == null || token.isBlank()) {
            token = extractAccessTokenFromResponse(response);
            if (token != null && !token.isBlank()) parsed.setAccessToken(token);
        }
        if (token == null || token.isBlank()) return null;
        fillLoginResponseFromJsonPath(response, parsed);
        String refreshToken = extractRefreshTokenFromResponse(response);
        TokenManager tm = TokenManager.getInstance();
        if (refreshToken != null && !refreshToken.isBlank()) {
            tm.setFromLoginWithRefresh(token, refreshToken, parsed.getUserId(), parsed.getProxyWalletAddress());
        } else {
            tm.setFromLogin(token, parsed.getUserId(), parsed.getProxyWalletAddress());
        }
        return parsed;
    }

    /**
     * User 1: POST /auth/refresh/token with stored refresh cookie. Fast path when access token is rejected (401) but cookie is valid.
     */
    public boolean refreshAccessTokenFromRefreshCookie() {
        TokenManager tm = TokenManager.getInstance();
        String cookieHeader = tm.getRefreshCookieHeaderValue();
        if (cookieHeader == null || cookieHeader.isBlank()) {
            return false;
        }
        Response response = refreshToken(cookieHeader);
        if (response == null || response.getStatusCode() != 200) {
            return false;
        }
        String newToken = extractAccessTokenFromResponse(response);
        if (newToken == null || newToken.isBlank()) {
            return false;
        }
        String newRefreshRaw = extractRefreshTokenFromResponse(response);
        if (newRefreshRaw != null && !newRefreshRaw.isBlank()) {
            tm.setFromLoginWithRefresh(newToken, newRefreshRaw, tm.getUserId(), tm.getProxyWalletAddress(), tm.getEoa(), tm.getPrivateKey());
        } else {
            tm.setAccessToken(newToken);
            tm.setTokenSetAtMs(System.currentTimeMillis());
        }
        return true;
    }

    /**
     * User 1: full login-with-signature using stored refresh cookie (no "expiring soon" gate).
     * Use when 401 persists after {@link #refreshAccessTokenFromRefreshCookie()} or refresh endpoint is unavailable.
     */
    public boolean loginWithRefreshCookieAndStore() {
        TokenManager tm = TokenManager.getInstance();
        if (!tm.hasToken() || tm.getRefreshCookieHeaderValue().isBlank()) {
            return false;
        }
        String apiKey = tm.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = Config.getApiKey();
        }
        if (apiKey == null || apiKey.isBlank()) {
            return false;
        }
        String eoa = tm.getEoa();
        String privateKey = tm.getPrivateKey();
        if (privateKey == null || privateKey.isBlank()) {
            privateKey = Config.getPrivateKey();
        }
        SignatureService sigService = new SignatureService();
        SignCreateProxyResponse sigResp = sigService.signCreateProxy(Config.getSigServerUrl(), privateKey);
        if (sigResp == null || !sigResp.isOk() || sigResp.getSignature() == null || sigResp.getSignature().isBlank()) {
            return false;
        }
        long now = System.currentTimeMillis();
        LoginRequest loginRequest = LoginRequest.builder()
                .walletAddress(sigResp.getWalletAddress())
                .signature(sigResp.getSignature())
                .message("Sign in to PRED Trading Platform")
                .nonce("nonce-" + now + "-" + (now / 1000))
                .chainType("base-sepolia")
                .timestamp(now / 1000)
                .build();
        Response response = login(apiKey, loginRequest, tm.getRefreshCookieHeaderValue());
        boolean ok = loginAndStoreFromResponse(response) != null;
        if (ok && (eoa != null || privateKey != null)) {
            if (eoa != null && !eoa.isBlank()) {
                tm.setEoa(eoa);
            }
            if (privateKey != null && !privateKey.isBlank()) {
                tm.setPrivateKey(privateKey);
            }
        }
        return ok;
    }

    /**
     * After a 401 on user 1, try refresh endpoint then full cookie login. Unlike {@link #refreshIfExpiringSoon()}, does not require token age &gt; 40 min.
     */
    public boolean refreshUser1SessionAfter401() {
        if (refreshAccessTokenFromRefreshCookie()) {
            return true;
        }
        return loginWithRefreshCookieAndStore();
    }

    /**
     * Proactive refresh: if token is expiring soon (e.g. after 40 min), get new access token via refresh cookie.
     * Uses stored apiKey and sig-server for a fresh login body. Restores EOA and privateKey after refresh.
     * Call from @BeforeMethod so long runs stay authenticated. No-op if no token, not expiring soon, or no refresh cookie.
     */
    public boolean refreshIfExpiringSoon() {
        TokenManager tm = TokenManager.getInstance();
        if (!tm.hasToken() || !tm.isTokenExpiringSoon() || tm.getRefreshCookieHeaderValue().isBlank()) {
            return false;
        }
        return loginWithRefreshCookieAndStore();
    }

    /**
     * Refresh User 2's access token via POST /auth/refresh/token using User 2's refresh cookie,
     * then write new session to .env.session2 and clear SecondUserContext cache.
     * Call from suite (e.g. BaseApiTest) so User 2 stays valid during long runs.
     * @return true if User 2 was present, had refresh cookie, refresh returned 200, and session was written
     */
    public boolean refreshSecondUserAndStore() {
        UserSession user2 = SecondUserContext.getSecondUser();
        if (user2 == null || !user2.hasToken()) return false;
        String cookie = user2.getRefreshCookieHeaderValue();
        if (cookie == null || cookie.isBlank()) return false;
        Response response = refreshToken(cookie);
        if (response == null || response.getStatusCode() != 200) return false;
        String newToken = extractAccessTokenFromResponse(response);
        if (newToken == null || newToken.isBlank()) return false;
        String newRefreshRaw = extractRefreshTokenFromResponse(response);
        String newRefresh = (newRefreshRaw != null && !newRefreshRaw.isBlank())
                ? ("refresh_token=" + newRefreshRaw)
                : cookie;
        boolean written = SessionFileWriter.writeSecondUser(
                newToken, newRefresh, user2.getUserId(), user2.getProxy(),
                user2.getEoa() != null ? user2.getEoa() : "",
                user2.getPrivateKey() != null ? user2.getPrivateKey() : "");
        if (written) SecondUserContext.clear();
        return written;
    }
}
