package com.pred.apitests.model.request;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Body for POST /api/v1/cashflow/deposit (public). Uses transaction_hash from internal deposit response.
 */
public class CashflowDepositRequest {

    private long salt;
    @JsonProperty("transaction_hash")
    private String transactionHash;
    private long timestamp;

    public long getSalt() { return salt; }
    public void setSalt(long salt) { this.salt = salt; }
    public String getTransactionHash() { return transactionHash; }
    public void setTransactionHash(String transactionHash) { this.transactionHash = transactionHash; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final CashflowDepositRequest o = new CashflowDepositRequest();

        public Builder salt(long salt) { o.salt = salt; return this; }
        public Builder transactionHash(String transactionHash) { o.transactionHash = transactionHash; return this; }
        public Builder timestamp(long timestamp) { o.timestamp = timestamp; return this; }
        public CashflowDepositRequest build() { return o; }
    }
}
