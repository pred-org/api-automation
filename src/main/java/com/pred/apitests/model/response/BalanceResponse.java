package com.pred.apitests.model.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BalanceResponse {

    private boolean success;

    @JsonProperty("usdc_balance")
    private String usdcBalance;

    @JsonProperty("position_balance")
    private String positionBalance;

    @JsonProperty("reserved_balance")
    private String reservedBalance;

    @JsonProperty("available_balance")
    private String availableBalance;

    @JsonProperty("total_balance")
    private String totalBalance;

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public String getUsdcBalance() { return usdcBalance; }
    public void setUsdcBalance(String usdcBalance) { this.usdcBalance = usdcBalance; }
    public String getPositionBalance() { return positionBalance; }
    public void setPositionBalance(String positionBalance) { this.positionBalance = positionBalance; }
    public String getReservedBalance() { return reservedBalance; }
    public void setReservedBalance(String reservedBalance) { this.reservedBalance = reservedBalance; }
    public String getAvailableBalance() { return availableBalance; }
    public void setAvailableBalance(String availableBalance) { this.availableBalance = availableBalance; }
    public String getTotalBalance() { return totalBalance; }
    public void setTotalBalance(String totalBalance) { this.totalBalance = totalBalance; }
}
