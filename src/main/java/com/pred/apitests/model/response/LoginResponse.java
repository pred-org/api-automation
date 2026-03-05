package com.pred.apitests.model.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response from login-with-signature. Access token and user context for later requests.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class LoginResponse {

    @JsonProperty("access_token")
    private String accessToken;

    @JsonProperty("data")
    private LoginResponseData data;

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public LoginResponseData getData() {
        return data;
    }

    public void setData(LoginResponseData data) {
        this.data = data;
    }

    /** Access token from top-level or from data (API may wrap in data). */
    public String getAccessToken() {
        if (accessToken != null && !accessToken.isBlank()) return accessToken;
        if (data != null && data.getAccessToken() != null) return data.getAccessToken();
        return null;
    }

    /** User id from data or top-level. */
    public String getUserId() {
        if (data != null && data.getUserId() != null) return data.getUserId();
        return null;
    }

    /** Proxy wallet from data (proxy_wallet_address or proxy_wallet_addr). */
    public String getProxyWalletAddress() {
        if (data != null) {
            if (data.getProxyWalletAddress() != null) return data.getProxyWalletAddress();
            if (data.getProxyWalletAddr() != null) return data.getProxyWalletAddr();
        }
        return null;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LoginResponseData {
        @JsonProperty("access_token")
        private String accessToken;
        @JsonProperty("user_id")
        private String userId;
        @JsonProperty("proxy_wallet_address")
        private String proxyWalletAddress;
        @JsonProperty("proxy_wallet_addr")
        private String proxyWalletAddr;

        public String getAccessToken() { return accessToken; }
        public void setAccessToken(String accessToken) { this.accessToken = accessToken; }
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        public String getProxyWalletAddress() { return proxyWalletAddress; }
        public void setProxyWalletAddress(String proxyWalletAddress) { this.proxyWalletAddress = proxyWalletAddress; }
        public String getProxyWalletAddr() { return proxyWalletAddr; }
        public void setProxyWalletAddr(String proxyWalletAddr) { this.proxyWalletAddr = proxyWalletAddr; }
    }
}
