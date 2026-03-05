package com.pred.apitests.util;

/**
 * Single-user token holder. Stores access token (and optionally user id, proxy) after login.
 * Call setToken/setUser after login; getToken() for Bearer in later requests.
 * Refresh is cookie-based: capture Set-Cookie: refresh_token after login; send Cookie on every request; when token is near expiry (~40 min), re-call login with refresh cookie.
 */
public final class TokenManager {

    private static final TokenManager INSTANCE = new TokenManager();

    /** Consider token expiring soon after this many milliseconds (40 min). */
    public static final long EXPIRY_THRESHOLD_MS = 40 * 60 * 1000L;

    private String accessToken;
    private String refreshToken;
    private String apiKey;
    private long tokenSetAtMs;
    private String userId;
    private String proxyWalletAddress;
    private String eoa;
    private String privateKey;

    private TokenManager() {}

    public static TokenManager getInstance() {
        return INSTANCE;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getProxyWalletAddress() {
        return proxyWalletAddress;
    }

    public void setProxyWalletAddress(String proxyWalletAddress) {
        this.proxyWalletAddress = proxyWalletAddress;
    }

    public String getEoa() { return eoa; }
    public void setEoa(String eoa) { this.eoa = eoa; }
    public String getPrivateKey() { return privateKey; }
    public void setPrivateKey(String privateKey) { this.privateKey = privateKey; }

    /** API key used for login (and refresh). Stored so refresh can run without config. */
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    /** Refresh token value (from Set-Cookie: refresh_token=...). Sent as Cookie on every authenticated request. */
    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    /** Time when access_token was set (System.currentTimeMillis()). Used for isTokenExpiringSoon(). */
    public long getTokenSetAtMs() {
        return tokenSetAtMs;
    }

    public void setTokenSetAtMs(long tokenSetAtMs) {
        this.tokenSetAtMs = tokenSetAtMs;
    }

    /** Set token and user context after login. */
    public void setFromLogin(String accessToken, String userId, String proxyWalletAddress) {
        this.accessToken = accessToken;
        this.userId = userId;
        this.proxyWalletAddress = proxyWalletAddress;
        this.tokenSetAtMs = System.currentTimeMillis();
    }

    /** Set full session after login, including refresh cookie. Optional eoa and privateKey for sig-server. */
    public void setFromLoginWithRefresh(String accessToken, String refreshToken, String userId, String proxyWalletAddress) {
        setFromLoginWithRefresh(accessToken, refreshToken, userId, proxyWalletAddress, null, null);
    }

    /** Set full session after login, including refresh cookie, eoa and privateKey. */
    public void setFromLoginWithRefresh(String accessToken, String refreshToken, String userId, String proxyWalletAddress, String eoa, String privateKey) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.userId = userId;
        this.proxyWalletAddress = proxyWalletAddress;
        this.eoa = eoa;
        this.privateKey = privateKey;
        this.tokenSetAtMs = System.currentTimeMillis();
    }

    /** True if token was set more than EXPIRY_THRESHOLD_MS ago (default 40 min). Call login again with refresh cookie. */
    public boolean isTokenExpiringSoon() {
        if (tokenSetAtMs <= 0) return true;
        return System.currentTimeMillis() - tokenSetAtMs >= EXPIRY_THRESHOLD_MS;
    }

    /** Cookie header value for requests: "refresh_token=<value>". Empty string if no refresh token. */
    public String getRefreshCookieHeaderValue() {
        if (refreshToken == null || refreshToken.isBlank()) return "";
        return "refresh_token=" + refreshToken;
    }

    /** Clear stored token and user. */
    public void clear() {
        this.accessToken = null;
        this.refreshToken = null;
        this.apiKey = null;
        this.tokenSetAtMs = 0L;
        this.userId = null;
        this.proxyWalletAddress = null;
        this.eoa = null;
        this.privateKey = null;
    }

    public boolean hasToken() {
        return accessToken != null && !accessToken.isBlank();
    }
}

