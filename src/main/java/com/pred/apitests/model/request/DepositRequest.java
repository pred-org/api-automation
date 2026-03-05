package com.pred.apitests.model.request;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DepositRequest {

    @JsonProperty("user_id")
    private String userId;

    private long amount;

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public long getAmount() { return amount; }
    public void setAmount(long amount) { this.amount = amount; }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final DepositRequest o = new DepositRequest();

        public Builder userId(String userId) { o.userId = userId; return this; }
        public Builder amount(long amount) { o.amount = amount; return this; }
        public DepositRequest build() { return o; }
    }
}
