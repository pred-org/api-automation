package com.pred.apitests.model.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class SignCreateProxyResponse {

    private boolean ok;

    @JsonProperty("wallet_address")
    private String walletAddress;

    private String signature;

    @JsonProperty("private_key")
    private String privateKey;

    /** Opaque id from sig-server when the wallet is registered in-process (POST /wallets). */
    @JsonProperty("signing_id")
    private String signingId;

    public boolean isOk() { return ok; }
    public void setOk(boolean ok) { this.ok = ok; }
    public String getWalletAddress() { return walletAddress; }
    public void setWalletAddress(String walletAddress) { this.walletAddress = walletAddress; }
    public String getSignature() { return signature; }
    public void setSignature(String signature) { this.signature = signature; }
    public String getPrivateKey() { return privateKey; }
    public void setPrivateKey(String privateKey) { this.privateKey = privateKey; }
    public String getSigningId() { return signingId; }
    public void setSigningId(String signingId) { this.signingId = signingId; }
}
