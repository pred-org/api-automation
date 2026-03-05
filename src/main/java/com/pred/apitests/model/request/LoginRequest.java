package com.pred.apitests.model.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request body for login-with-signature. EIP-712 signature produced by sig-server or external signer.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LoginRequest {

    @JsonProperty("data")
    private LoginRequestData data;

    public LoginRequest() {}

    public LoginRequest(LoginRequestData data) {
        this.data = data;
    }

    public LoginRequestData getData() {
        return data;
    }

    public void setData(LoginRequestData data) {
        this.data = data;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private LoginRequestData data;

        public Builder data(LoginRequestData data) {
            this.data = data;
            return this;
        }

        public Builder walletAddress(String walletAddress) {
            if (data == null) data = new LoginRequestData();
            data.setWalletAddress(walletAddress);
            return this;
        }

        public Builder signature(String signature) {
            if (data == null) data = new LoginRequestData();
            data.setSignature(signature);
            return this;
        }

        public Builder message(String message) {
            if (data == null) data = new LoginRequestData();
            data.setMessage(message);
            return this;
        }

        public Builder nonce(String nonce) {
            if (data == null) data = new LoginRequestData();
            data.setNonce(nonce);
            return this;
        }

        public Builder chainType(String chainType) {
            if (data == null) data = new LoginRequestData();
            data.setChainType(chainType);
            return this;
        }

        public Builder timestamp(long timestamp) {
            if (data == null) data = new LoginRequestData();
            data.setTimestamp(timestamp);
            return this;
        }

        public LoginRequest build() {
            return new LoginRequest(data);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class LoginRequestData {
        @JsonProperty("wallet_address")
        private String walletAddress;
        @JsonProperty("signature")
        private String signature;
        @JsonProperty("message")
        private String message;
        @JsonProperty("nonce")
        private String nonce;
        @JsonProperty("chain_type")
        private String chainType;
        @JsonProperty("timestamp")
        private Long timestamp;

        public String getWalletAddress() { return walletAddress; }
        public void setWalletAddress(String walletAddress) { this.walletAddress = walletAddress; }
        public String getSignature() { return signature; }
        public void setSignature(String signature) { this.signature = signature; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public String getNonce() { return nonce; }
        public void setNonce(String nonce) { this.nonce = nonce; }
        public String getChainType() { return chainType; }
        public void setChainType(String chainType) { this.chainType = chainType; }
        public Long getTimestamp() { return timestamp; }
        public void setTimestamp(Long timestamp) { this.timestamp = timestamp; }
    }
}
