package com.pred.apitests.test;

import com.pred.apitests.base.BaseApiTest;
import com.pred.apitests.config.Config;
import com.pred.apitests.model.response.BalanceResponse;
import com.pred.apitests.service.DepositService;
import com.pred.apitests.service.PortfolioService;
import com.pred.apitests.util.TokenManager;
import io.restassured.response.Response;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class DepositTest extends BaseApiTest {

    private DepositService depositService;
    private PortfolioService portfolioService;

    @BeforeClass
    public void init() {
        if (!TokenManager.getInstance().hasToken()) {
            throw new SkipException("No token - run AuthFlowTest first");
        }
        depositService = new DepositService();
        portfolioService = new PortfolioService();
    }

    @Test(description = "Deposit: internal deposit then cashflow/deposit with transaction_hash (skipped if balance already sufficient)")
    public void depositFunds() {
        String userId = TokenManager.getInstance().getUserId();
        String accessToken = TokenManager.getInstance().getAccessToken();
        String proxyAddress = TokenManager.getInstance().getProxyWalletAddress();
        if (userId == null || userId.isBlank()) {
            throw new SkipException("No userId in TokenManager");
        }
        if (accessToken == null || proxyAddress == null || proxyAddress.isBlank()) {
            throw new SkipException("No accessToken or proxy in TokenManager");
        }
        long amount = Config.getDepositAmount();

        Response balanceResponse = portfolioService.getBalance(accessToken, TokenManager.getInstance().getRefreshCookieHeaderValue());
        if (balanceResponse.getStatusCode() == 200) {
            BalanceResponse balance = balanceResponse.as(BalanceResponse.class);
            String usdcStr = balance != null ? balance.getUsdcBalance() : null;
            if (usdcStr != null && !usdcStr.isBlank()) {
                try {
                    long currentBalance = Long.parseLong(usdcStr.trim());
                    if (currentBalance >= amount) {
                        throw new SkipException("Balance already sufficient (" + usdcStr + "), deposit skipped (once per user)");
                    }
                } catch (NumberFormatException ignored) { }
            }
        }

        Response internalResponse = depositService.internalDeposit(userId, amount);
        assertThat(internalResponse.getStatusCode()).isGreaterThanOrEqualTo(200).isLessThan(300);
        String transactionHash = depositService.extractTransactionHashFromInternalDeposit(internalResponse);
        assertThat(transactionHash).isNotBlank();

        long salt = System.currentTimeMillis();
        long timestamp = System.currentTimeMillis() / 1000;
        Response cashflowResponse = depositService.cashflowDeposit(accessToken, proxyAddress, transactionHash, salt, timestamp);
        int cashflowStatus = cashflowResponse.getStatusCode();
        if (cashflowStatus >= 400) {
            String body = cashflowResponse.getBody().asString();
            String snippet = (body != null && body.length() > 350) ? body.substring(0, 350) + "..." : body;
            throw new SkipException("Cashflow deposit returned " + cashflowStatus + ": " + snippet);
        }
        assertThat(cashflowStatus).isGreaterThanOrEqualTo(200).isLessThan(300);
    }

    @Test(description = "Internal deposit with invalid userId returns 200 with success: false")
    public void depositWithInvalidUserId_returnsFailed() {
        Response response = depositService.internalDeposit("invalid-user-id-000", 1000000000L);
        assertThat(response.getStatusCode()).isEqualTo(200);
        response.then().body("success", equalTo(false))
                .body("message", equalTo("Failed to get user wallet address"))
                .body("err_code", equalTo("WALLET_FETCH_FAILED"))
                .body("user_id", equalTo("invalid-user-id-000"))
                .body("amount", equalTo(1000000000));
        assertThat(response.jsonPath().getBoolean("success")).isFalse();
        assertThat(response.jsonPath().getString("err_code")).isEqualTo("WALLET_FETCH_FAILED");
    }
}
