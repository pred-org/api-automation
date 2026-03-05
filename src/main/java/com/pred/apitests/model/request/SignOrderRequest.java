package com.pred.apitests.model.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class SignOrderRequest {

    private String salt;
    private String price;
    private String quantity;

    @JsonProperty("questionId")
    private String questionId;

    @JsonProperty("feeRateBps")
    private Integer feeRateBps;

    private Integer intent;

    @JsonProperty("signatureType")
    private Integer signatureType;

    private String maker;
    private String signer;

    @JsonProperty("taker")
    private String taker;

    @JsonProperty("expiration")
    private String expiration;

    @JsonProperty("nonce")
    private String nonce;

    @JsonProperty("priceInCents")
    private Boolean priceInCents;

    @JsonProperty("timestamp")
    private Long timestamp;

    @JsonProperty("privateKey")
    private String privateKey;

    public String getSalt() { return salt; }
    public void setSalt(String salt) { this.salt = salt; }
    public String getPrice() { return price; }
    public void setPrice(String price) { this.price = price; }
    public String getQuantity() { return quantity; }
    public void setQuantity(String quantity) { this.quantity = quantity; }
    public String getQuestionId() { return questionId; }
    public void setQuestionId(String questionId) { this.questionId = questionId; }
    public Integer getFeeRateBps() { return feeRateBps; }
    public void setFeeRateBps(Integer feeRateBps) { this.feeRateBps = feeRateBps; }
    public Integer getIntent() { return intent; }
    public void setIntent(Integer intent) { this.intent = intent; }
    public Integer getSignatureType() { return signatureType; }
    public void setSignatureType(Integer signatureType) { this.signatureType = signatureType; }
    public String getMaker() { return maker; }
    public void setMaker(String maker) { this.maker = maker; }
    public String getSigner() { return signer; }
    public void setSigner(String signer) { this.signer = signer; }
    public String getTaker() { return taker; }
    public void setTaker(String taker) { this.taker = taker; }
    public String getExpiration() { return expiration; }
    public void setExpiration(String expiration) { this.expiration = expiration; }
    public String getNonce() { return nonce; }
    public void setNonce(String nonce) { this.nonce = nonce; }
    public Boolean getPriceInCents() { return priceInCents; }
    public void setPriceInCents(Boolean priceInCents) { this.priceInCents = priceInCents; }
    public Long getTimestamp() { return timestamp; }
    public void setTimestamp(Long timestamp) { this.timestamp = timestamp; }
    public String getPrivateKey() { return privateKey; }
    public void setPrivateKey(String privateKey) { this.privateKey = privateKey; }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final SignOrderRequest o = new SignOrderRequest();

        public Builder salt(String salt) { o.salt = salt; return this; }
        public Builder price(String price) { o.price = price; return this; }
        public Builder quantity(String quantity) { o.quantity = quantity; return this; }
        public Builder questionId(String questionId) { o.questionId = questionId; return this; }
        public Builder feeRateBps(Integer feeRateBps) { o.feeRateBps = feeRateBps; return this; }
        public Builder intent(Integer intent) { o.intent = intent; return this; }
        public Builder signatureType(Integer signatureType) { o.signatureType = signatureType; return this; }
        public Builder maker(String maker) { o.maker = maker; return this; }
        public Builder signer(String signer) { o.signer = signer; return this; }
        public Builder taker(String taker) { o.taker = taker; return this; }
        public Builder expiration(String expiration) { o.expiration = expiration; return this; }
        public Builder nonce(String nonce) { o.nonce = nonce; return this; }
        public Builder priceInCents(Boolean priceInCents) { o.priceInCents = priceInCents; return this; }
        public Builder timestamp(Long timestamp) { o.timestamp = timestamp; return this; }
        public Builder privateKey(String privateKey) { o.privateKey = privateKey; return this; }
        public SignOrderRequest build() { return o; }
    }
}
