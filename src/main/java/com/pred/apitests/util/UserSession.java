package com.pred.apitests.util;

/**
 * Holds one user's session for API calls (place order, balance, etc.).
 * User 1 is typically from TokenManager; user 2 from SecondUserContext.
 */
public final class UserSession {

    private final String accessToken;
    private final String refreshCookie;
    private final String userId;
    private final String proxy;
    private final String eoa;
    private final String privateKey;

    public UserSession(String accessToken, String refreshCookie, String userId, String proxy, String eoa, String privateKey) {
        this.accessToken = accessToken;
        this.refreshCookie = refreshCookie != null ? refreshCookie : "";
        this.userId = userId;
        this.proxy = proxy;
        this.eoa = eoa;
        this.privateKey = privateKey;
    }

    public String getAccessToken() { return accessToken; }
    public String getRefreshCookie() { return refreshCookie; }
    public String getUserId() { return userId; }
    public String getProxy() { return proxy; }
    public String getEoa() { return eoa; }
    /** Optional: for sign-order (sig-server). If blank, use Config / env for that user. */
    public String getPrivateKey() { return privateKey; }

    public boolean hasToken() {
        return accessToken != null && !accessToken.isBlank();
    }
}
