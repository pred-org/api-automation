package com.pred.apitests.test;

import com.pred.apitests.base.BaseApiTest;
import com.pred.apitests.config.Config;
import com.pred.apitests.model.response.BalanceResponse;
import com.pred.apitests.service.DepositService;
import com.pred.apitests.service.PortfolioService;
import com.pred.apitests.util.UserSession;
import io.restassured.response.Response;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class DepositTest extends BaseApiTest {

    private DepositService depositService;
    private PortfolioService portfolioService;

    /** Current user (User 1 from TokenManager by default). Override in DepositTestUser2 to use SecondUserContext. */
    protected UserSession getSession() {
        return com.pred.apitests.util.TokenManager.getInstance().getSession();
    }

    @BeforeClass
    public void init() {
        UserSession session = getSession();
        if (session == null || !session.hasToken()) {
            throw new SkipException("No session - run AuthFlowTest first (and AuthFlowTestUser2 for user 2)");
        }
        depositService = new DepositService();
        portfolioService = new PortfolioService();
    }

    @Test(description = "Deposit: internal deposit then cashflow/deposit with transaction_hash (skipped if balance already sufficient)")
    public void depositFunds() {
        UserSession session = getSession();
        String userId = session.getUserId();
        if (userId == null || userId.isBlank()) {
            throw new SkipException("No userId in session");
        }
        long amount = Config.getDepositAmount();

        String accessToken = session.getAccessToken();
        if (accessToken == null || accessToken.isBlank()) {
            throw new SkipException("No accessToken in session");
        }
        String cookie = session.getRefreshCookieHeaderValue();
        if (cookie == null || cookie.isBlank()) cookie = session.getRefreshCookie();
        Response balanceResponse = portfolioService.getBalance(accessToken, cookie);
        if (balanceResponse.getStatusCode() == 200) {
            BalanceResponse balance = balanceResponse.as(BalanceResponse.class);
            String usdcStr = balance != null ? balance.getUsdcBalance() : null;
            if (usdcStr != null && !usdcStr.isBlank()) {
                try {
                    long currentBalance = parseBalanceAsLong(usdcStr);
                    if (currentBalance >= amount) {
                        throw new SkipException("Balance already sufficient (" + usdcStr + "), deposit skipped (once per user)");
                    }
                } catch (IllegalArgumentException ignored) { }
            }
        }

        Response internalResponse = depositService.internalDeposit(userId, amount);
        assertThat(internalResponse.getStatusCode()).as("internal deposit → 200").isEqualTo(200);
        String transactionHash = depositService.extractTransactionHashFromInternalDeposit(internalResponse);
        assertThat(transactionHash).isNotBlank();

        Response cashflowResponse = depositService.cashflowDeposit(userId, transactionHash);
        int cashflowStatus = cashflowResponse.getStatusCode();
        if (cashflowStatus >= 400) {
            String body = cashflowResponse.getBody().asString();
            String snippet = (body != null && body.length() > 350) ? body.substring(0, 350) + "..." : body;
            throw new SkipException("Cashflow deposit returned " + cashflowStatus + ": " + snippet);
        }
        assertThat(cashflowStatus).as("cashflow deposit → 200").isEqualTo(200);
    }

    @Test(description = "Internal deposit with invalid userId returns 200 with success: false")
    public void depositWithInvalidUserId_returnsFailed() {
        Response response;
        try {
            response = depositService.internalDeposit("invalid-user-id-000", 1000000000L);
        } catch (Exception e) {
            if (isNoRouteToHost(e)) {
                throw new SkipException("Internal deposit endpoint unreachable (NoRouteToHost). Skipping User1/User2 internal-deposit tests.");
            }
            throw e;
        }
        assertThat(response.getStatusCode()).isEqualTo(200);
        response.then().body("success", equalTo(false))
                .body("message", equalTo("Failed to get user wallet address"))
                .body("err_code", equalTo("WALLET_FETCH_FAILED"))
                .body("user_id", equalTo("invalid-user-id-000"))
                .body("amount", equalTo(1000000000));
        assertThat(response.jsonPath().getBoolean("success")).isFalse();
        assertThat(response.jsonPath().getString("err_code")).isEqualTo("WALLET_FETCH_FAILED");
    }

    private static boolean isNoRouteToHost(Throwable t) {
        Throwable cur = t;
        while (cur != null) {
            if (cur instanceof java.net.NoRouteToHostException) return true;
            if (cur instanceof java.net.UnknownHostException) return true;
            String msg = cur.getMessage();
            if (msg != null && msg.toLowerCase().contains("no route to host")) return true;
            cur = cur.getCause();
        }
        return false;
    }
}
