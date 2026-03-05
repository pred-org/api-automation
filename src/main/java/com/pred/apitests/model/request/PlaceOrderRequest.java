package com.pred.apitests.model.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class PlaceOrderRequest {

    private String salt;

    @JsonProperty("user_id")
    private String userId;

    @JsonProperty("market_id")
    private String marketId;

    private String side;

    @JsonProperty("token_id")
    private String tokenId;

    private String price;
    private String quantity;
    private String amount;

    @JsonProperty("is_low_priority")
    private Boolean isLowPriority;

    private String signature;
    private String type;
    private Long timestamp;

    @JsonProperty("reduce_only")
    private Boolean reduceOnly;

    @JsonProperty("fee_rate_bps")
    private Integer feeRateBps;

    public String getSalt() { return salt; }
    public void setSalt(String salt) { this.salt = salt; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getMarketId() { return marketId; }
    public void setMarketId(String marketId) { this.marketId = marketId; }
    public String getSide() { return side; }
    public void setSide(String side) { this.side = side; }
    public String getTokenId() { return tokenId; }
    public void setTokenId(String tokenId) { this.tokenId = tokenId; }
    public String getPrice() { return price; }
    public void setPrice(String price) { this.price = price; }
    public String getQuantity() { return quantity; }
    public void setQuantity(String quantity) { this.quantity = quantity; }
    public String getAmount() { return amount; }
    public void setAmount(String amount) { this.amount = amount; }
    public Boolean getIsLowPriority() { return isLowPriority; }
    public void setIsLowPriority(Boolean isLowPriority) { this.isLowPriority = isLowPriority; }
    public String getSignature() { return signature; }
    public void setSignature(String signature) { this.signature = signature; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public Long getTimestamp() { return timestamp; }
    public void setTimestamp(Long timestamp) { this.timestamp = timestamp; }
    public Boolean getReduceOnly() { return reduceOnly; }
    public void setReduceOnly(Boolean reduceOnly) { this.reduceOnly = reduceOnly; }
    public Integer getFeeRateBps() { return feeRateBps; }
    public void setFeeRateBps(Integer feeRateBps) { this.feeRateBps = feeRateBps; }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final PlaceOrderRequest o = new PlaceOrderRequest();

        public Builder salt(String salt) { o.salt = salt; return this; }
        public Builder userId(String userId) { o.userId = userId; return this; }
        public Builder marketId(String marketId) { o.marketId = marketId; return this; }
        public Builder side(String side) { o.side = side; return this; }
        public Builder tokenId(String tokenId) { o.tokenId = tokenId; return this; }
        public Builder price(String price) { o.price = price; return this; }
        public Builder quantity(String quantity) { o.quantity = quantity; return this; }
        public Builder amount(String amount) { o.amount = amount; return this; }
        public Builder isLowPriority(Boolean isLowPriority) { o.isLowPriority = isLowPriority; return this; }
        public Builder signature(String signature) { o.signature = signature; return this; }
        public Builder type(String type) { o.type = type; return this; }
        public Builder timestamp(Long timestamp) { o.timestamp = timestamp; return this; }
        public Builder reduceOnly(Boolean reduceOnly) { o.reduceOnly = reduceOnly; return this; }
        public Builder feeRateBps(Integer feeRateBps) { o.feeRateBps = feeRateBps; return this; }
        public PlaceOrderRequest build() { return o; }
    }
}
